package com.example

import com.google.gson.Gson
import com.google.gson.JsonParser
import io.ktor.client.HttpClient
import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsText
import io.ktor.http.*
import io.ktor.server.testing.testApplication
import org.junit.Assume.assumeTrue
import java.security.MessageDigest
import java.sql.DriverManager
import java.util.Base64
import java.util.UUID
import kotlin.test.*

class SecurityPostgresTest {
    private val gson = Gson()

    @Test
    fun `postgres enforces recipe ownership and rejects expired and revoked sessions`() = testApplication {
        val connect = testConnection()
        application { configureCookBook(connect = connect) }

        val suffix = UUID.randomUUID().toString().take(8)
        val ownerEmail = "o'neal-$suffix@example.test"
        val otherEmail = "other-$suffix@example.test"
        val password = "fixture-only-password"
        val ownerId = client.register("owner-$suffix", ownerEmail, password)
        val otherId = client.register("other-$suffix", otherEmail, password)
        val duplicateEmail = client.post("/auth/register") {
            setBody(gson.toJson(mapOf("username" to "duplicate-$suffix", "email" to ownerEmail, "password" to password)))
        }
        assertEquals(HttpStatusCode.Conflict, duplicateEmail.status)
        val duplicateUsername = client.post("/auth/register") {
            setBody(gson.toJson(mapOf("username" to "owner-$suffix", "email" to "duplicate-$suffix@example.test", "password" to password)))
        }
        assertEquals(HttpStatusCode.Conflict, duplicateUsername.status)
        val wrongPassword = client.post("/auth/login") {
            setBody(gson.toJson(mapOf("email" to ownerEmail, "password" to "another-fixture-password")))
        }
        assertEquals(HttpStatusCode.Unauthorized, wrongPassword.status)
        assertFalse(JsonParser.parseString(wrongPassword.bodyAsText()).asJsonObject.has("token"))
        val owner = client.login(ownerEmail, password)
        val other = client.login(otherEmail, password)
        val me = client.get("/auth/me") { bearerAuth(owner) }
        assertEquals(HttpStatusCode.OK, me.status)
        assertEquals("no-store", me.headers[HttpHeaders.CacheControl])

        val title = "Przepis O'Neala; $suffix"
        val add = client.post("/recipes") { bearerAuth(owner); setBody(recipe(title)) }
        assertEquals(HttpStatusCode.Created, add.status)
        val id = JsonParser.parseString(add.bodyAsText()).asJsonObject["id"].asInt
        val path = "/recipes/$id"

        val publicList = client.get("/recipes")
        val ownerList = client.get("/recipes/mine") { bearerAuth(owner) }
        val otherList = client.get("/recipes/mine") { bearerAuth(other) }
        assertEquals(HttpStatusCode.OK, publicList.status)
        assertEquals(HttpStatusCode.OK, ownerList.status)
        assertEquals(HttpStatusCode.OK, otherList.status)
        assertTrue(JsonParser.parseString(publicList.bodyAsText()).asJsonArray.any { it.asJsonObject["id"].asInt == id })
        assertTrue(JsonParser.parseString(ownerList.bodyAsText()).asJsonArray.any { it.asJsonObject["id"].asInt == id })
        assertTrue(JsonParser.parseString(otherList.bodyAsText()).asJsonArray.isEmpty)

        val rejected = client.put(path) { bearerAuth(other); setBody(recipe("Obcy tytuł")) }
        assertEquals(HttpStatusCode.NotFound, rejected.status)
        assertEquals(HttpStatusCode.NotFound, client.delete(path) { bearerAuth(other) }.status)
        val original = client.get(path)
        assertEquals(HttpStatusCode.OK, original.status)
        assertEquals(title, JsonParser.parseString(original.bodyAsText()).asJsonObject["title"].asString)

        val invalidRecipe = """{"title":"Test","preparationTime":"20 min","ingredients":[null],"steps":["Mieszaj"]}"""
        assertEquals(HttpStatusCode.BadRequest, client.put(path) { bearerAuth(owner); setBody(invalidRecipe) }.status)
        assertEquals(HttpStatusCode.OK, client.put(path) { bearerAuth(owner); setBody(recipe("Poprawiony przepis")) }.status)

        val expired = "expired-$suffix"
        connect().use { connection ->
            connection.prepareStatement(
                "INSERT INTO user_sessions (token_hash, user_id, expires_at) VALUES (?, ?, NOW() - INTERVAL '1 minute')"
            ).use { statement ->
                statement.setString(1, sha256(expired))
                statement.setInt(2, ownerId)
                statement.executeUpdate()
            }
            connection.prepareStatement("SELECT password_hash FROM users WHERE id IN (?, ?)").use { statement ->
                statement.setInt(1, ownerId)
                statement.setInt(2, otherId)
                val hashes = statement.executeQuery().use { result ->
                    buildList { while (result.next()) add(result.getString(1)) }
                }
                assertEquals(2, hashes.toSet().size)
                assertTrue(hashes.all { it.startsWith("pbkdf2_sha256:600000:") && !it.contains(password) })
            }
            connection.prepareStatement("SELECT token_hash FROM user_sessions WHERE user_id = ?").use { statement ->
                statement.setInt(1, ownerId)
                val hashes = statement.executeQuery().use { result ->
                    buildSet { while (result.next()) add(result.getString(1)) }
                }
                assertEquals(setOf(sha256(owner), sha256(expired)), hashes)
            }
            connection.createStatement().use { statement ->
                statement.executeQuery(
                    "SELECT rolsuper, rolcreatedb, rolcreaterole, has_table_privilege(current_user,'users','DELETE') FROM pg_roles WHERE rolname = current_user"
                ).use { result ->
                    assertTrue(result.next())
                    for (column in 1..4) assertFalse(result.getBoolean(column))
                }
            }
        }

        assertEquals(HttpStatusCode.Unauthorized, client.get("/auth/me") { bearerAuth(expired) }.status)
        assertEquals(HttpStatusCode.Unauthorized, client.delete(path) { bearerAuth(expired) }.status)
        assertEquals(HttpStatusCode.OK, client.delete(path) { bearerAuth(owner) }.status)
        assertEquals(HttpStatusCode.NotFound, client.get(path).status)
        assertEquals(HttpStatusCode.NoContent, client.post("/auth/logout") { bearerAuth(owner) }.status)
        assertEquals(HttpStatusCode.Unauthorized, client.get("/recipes/mine") { bearerAuth(owner) }.status)
        assertEquals(HttpStatusCode.OK, client.get("/auth/me") { bearerAuth(other) }.status)
    }

    @Test
    fun `recipes and sessions survive starting a new backend instance`() {
        val connect = testConnection()
        val suffix = UUID.randomUUID().toString().take(8)
        val email = "restart-$suffix@example.test"
        val password = "fixture-only-password"
        val title = "Przepis po restarcie $suffix"
        lateinit var token: String
        var path = ""

        testApplication {
            application { configureCookBook(connect = connect) }
            client.register("restart-$suffix", email, password)
            token = client.login(email, password)
            val add = client.post("/recipes") { bearerAuth(token); setBody(recipe(title)) }
            assertEquals(HttpStatusCode.Created, add.status)
            path = "/recipes/${JsonParser.parseString(add.bodyAsText()).asJsonObject["id"].asInt}"
        }

        testApplication {
            application { configureCookBook(connect = connect) }
            assertEquals(HttpStatusCode.OK, client.get("/auth/me") { bearerAuth(token) }.status)
            val saved = client.get(path)
            assertEquals(HttpStatusCode.OK, saved.status)
            assertEquals(title, JsonParser.parseString(saved.bodyAsText()).asJsonObject["title"].asString)
            assertEquals(HttpStatusCode.OK, client.delete(path) { bearerAuth(token) }.status)
        }
    }

    private fun testConnection(): () -> java.sql.Connection {
        val url = System.getenv("COOKBOOK_TEST_DB_URL")
        assumeTrue("Start a disposable PostgreSQL fixture to run this test", !url.isNullOrBlank())
        val user = checkNotNull(System.getenv("COOKBOOK_TEST_DB_USER"))
        val password = checkNotNull(System.getenv("COOKBOOK_TEST_DB_PASSWORD"))
        return { DriverManager.getConnection(url, user, password) }
    }

    private suspend fun HttpClient.register(username: String, email: String, password: String): Int {
        val response = post("/auth/register") {
            setBody(gson.toJson(mapOf("username" to username, "email" to email, "password" to password)))
        }
        assertEquals(HttpStatusCode.Created, response.status)
        return JsonParser.parseString(response.bodyAsText()).asJsonObject["id"].asInt
    }

    private suspend fun HttpClient.login(email: String, password: String): String {
        val response = post("/auth/login") { setBody(gson.toJson(mapOf("email" to email, "password" to password))) }
        assertEquals(HttpStatusCode.OK, response.status)
        return JsonParser.parseString(response.bodyAsText()).asJsonObject["token"].asString
    }

    private fun recipe(title: String): String = gson.toJson(
        mapOf("title" to title, "preparationTime" to "20 min", "ingredients" to listOf("Pomidor"), "steps" to listOf("Pokrój"))
    )

    private fun sha256(value: String): String = Base64.getUrlEncoder().withoutPadding()
        .encodeToString(MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8)))
}
