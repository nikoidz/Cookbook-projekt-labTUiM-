package com.example

import com.google.gson.JsonParser
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import java.net.InetSocketAddress
import java.net.URI
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class ExternalRecipeTest {
    @Test
    fun `search maps recipe fields and encodes the upstream query`() {
        val upstreamQuery = AtomicReference<String>()
        withUpstream(handler = { exchange ->
            upstreamQuery.set(exchange.requestURI.rawQuery)
            exchange.respond(200, """
                {"meals":[{"idMeal":"52772","strMeal":"Pasta & pomidory",
                    "strInstructions":"Zmieszaj.\r\nPodaj.",
                    "strMealThumb":"https://www.themealdb.com/images/media/meals/pasta.jpg",
                    "strIngredient1":" Pomidory ","strMeasure1":" 2 ",
                    "strIngredient2":null,"strMeasure2":"3",
                    "strIngredient3":"Sól","strMeasure3":" ",
                    "strIngredient20":"Makaron","strMeasure20":"100 g"}]}
            """.trimIndent())
        }) { mealDb ->
            testApplication {
                application { routing { externalRecipeRoutes(mealDb) } }
                val response = client.get("/external/recipes?search=%20Pasta%20%26%20%C5%82%20")
                assertEquals(HttpStatusCode.OK, response.status)
                assertEquals("s=Pasta+%26+%C5%82", upstreamQuery.get())
                val recipe = JsonParser.parseString(response.bodyAsText()).asJsonArray.single().asJsonObject
                assertEquals("52772", recipe["id"].asString)
                assertEquals("Pasta & pomidory", recipe["title"].asString)
                assertEquals(listOf("2 Pomidory", "Sól", "100 g Makaron"), recipe["ingredients"].asJsonArray.map { it.asString })
                assertEquals("Zmieszaj.\r\nPodaj.", recipe["instructions"].asString)
                assertEquals("https://www.themealdb.com/images/media/meals/pasta.jpg", recipe["imageUrl"].asString)
            }
        }
    }

    @Test
    fun `empty searches return an empty JSON array`() {
        withUpstream(handler = { exchange ->
            val meals = if (exchange.requestURI.rawQuery == "s=nullable") "null" else "[]"
            exchange.respond(200, """{"meals":$meals}""")
        }) { mealDb ->
            testApplication {
                application { routing { externalRecipeRoutes(mealDb) } }
                for (term in listOf("nullable", "empty")) {
                    val response = client.get("/external/recipes?search=$term")
                    assertEquals(HttpStatusCode.OK, response.status)
                    assertEquals("[]", response.bodyAsText())
                }
            }
        }
    }

    @Test
    fun `invalid queries are rejected by the application route`() = testApplication {
        configure()
        for (suffix in listOf("", "?search=%20", "?search=${"x".repeat(101)}", "?search=x&search=y", "?search=a%0Ab")) {
            val response = client.get("/external/recipes$suffix")
            assertEquals(HttpStatusCode.BadRequest, response.status)
            assertFalse(JsonParser.parseString(response.bodyAsText()).asJsonObject["error"].asString.isBlank())
        }
    }

    @Test
    fun `upstream errors and invalid JSON return bad gateway`() {
        withUpstream(handler = { exchange ->
            when (exchange.requestURI.rawQuery) {
                "s=http-error" -> exchange.respond(503, "unavailable")
                "s=invalid-json" -> exchange.respond(200, "<html>error</html>")
                else -> exchange.respond(200, """{"meals":[null]}""")
            }
        }) { mealDb ->
            testApplication {
                application { routing { externalRecipeRoutes(mealDb) } }
                for (term in listOf("http-error", "invalid-json", "invalid-record")) {
                    assertEquals(HttpStatusCode.BadGateway, client.get("/external/recipes?search=$term").status)
                }
            }
        }
    }

    @Test
    fun `upstream timeout returns gateway timeout`() {
        val release = CountDownLatch(1)
        withUpstream(timeoutMillis = 250, handler = { exchange ->
            exchange.use {
                release.await(5, TimeUnit.SECONDS)
            }
        }) { mealDb ->
            try {
                testApplication {
                    application { routing { externalRecipeRoutes(mealDb) } }
                    assertEquals(HttpStatusCode.GatewayTimeout, client.get("/external/recipes?search=chicken").status)
                }
            } finally {
                release.countDown()
            }
        }
    }

    private fun withUpstream(
        timeoutMillis: Long = 5_000,
        handler: (HttpExchange) -> Unit,
        block: (MealDbClient) -> Unit
    ) {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/search", handler)
        server.start()
        try {
            block(MealDbClient(URI.create("http://127.0.0.1:${server.address.port}/search"), timeoutMillis))
        } finally {
            server.stop(0)
        }
    }

    private fun HttpExchange.respond(status: Int, body: String) = use { exchange ->
        val bytes = body.toByteArray()
        exchange.sendResponseHeaders(status, bytes.size.toLong())
        exchange.responseBody.write(bytes)
    }
}
