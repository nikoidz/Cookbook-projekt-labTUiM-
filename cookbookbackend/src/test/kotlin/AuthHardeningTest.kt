package com.example

import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsText
import io.ktor.http.*
import io.ktor.server.testing.testApplication
import java.sql.SQLException
import java.util.concurrent.TimeUnit
import kotlin.test.*

class AuthHardeningTest {
    @Test
    fun `login rate limit uses the direct connection and returns retry after`() = testApplication {
        application { configureCookBook { error("Invalid input must not query the database") } }
        repeat(10) { index ->
            val result = client.post("/auth/login") {
                header("X-Forwarded-For", "192.0.2.$index")
                setBody("{}")
            }
            assertEquals(HttpStatusCode.BadRequest, result.status)
        }
        val result = client.post("/auth/login") {
            header("X-Forwarded-For", "192.0.2.12")
            setBody("{}")
        }
        assertEquals(HttpStatusCode.TooManyRequests, result.status)
        assertTrue(checkNotNull(result.headers[HttpHeaders.RetryAfter]?.toIntOrNull()) in 1..60)
        assertEquals("no-store", result.headers[HttpHeaders.CacheControl])
        assertEquals(HttpStatusCode.BadRequest, client.post("/auth/register") { setBody("{}") }.status)
    }

    @Test
    fun `rate window expires independently for each address`() {
        var time = 0L
        val limiter = AuthRateLimiter(2) { time }
        assertNull(limiter.retryAfterSeconds("first"))
        assertNull(limiter.retryAfterSeconds("first"))
        assertEquals(60L, limiter.retryAfterSeconds("first"))
        assertNull(limiter.retryAfterSeconds("second"))
        time = TimeUnit.SECONDS.toNanos(59)
        assertEquals(1L, limiter.retryAfterSeconds("first"))
        time = TimeUnit.SECONDS.toNanos(60)
        assertNull(limiter.retryAfterSeconds("first"))
    }

    @Test
    fun `northflank limits clients by its ingress header`() = testApplication {
        application { configureCookBook(Ingress.NORTHFLANK) { error("Invalid input must not query the database") } }
        repeat(10) { index ->
            val response = client.post("/auth/login") {
                header("X-Envoy-External-Address", "192.0.2.10")
                header("X-Forwarded-For", "198.51.100.$index")
                setBody("{}")
            }
            assertEquals(HttpStatusCode.BadRequest, response.status)
        }
        val limited = client.post("/auth/login") {
            header("X-Envoy-External-Address", "192.0.2.10")
            setBody("{}")
        }
        assertEquals(HttpStatusCode.TooManyRequests, limited.status)
        val other = client.post("/auth/login") {
            header("X-Envoy-External-Address", "2001:db8::1")
            setBody("{}")
        }
        assertEquals(HttpStatusCode.BadRequest, other.status)
    }

    @Test
    fun `auth validates JSON types and rejects oversized requests before database access`() = testApplication {
        application { configureCookBook { error("Invalid input must not query the database") } }
        for (body in listOf("{email:1}", "[]", """{"email":"a@example.test","password":12345678}""")) {
            assertEquals(HttpStatusCode.BadRequest, client.post("/auth/login") { setBody(body) }.status)
        }
        val response = client.post("/auth/login") { setBody("x".repeat(AUTH_BODY_LIMIT.toInt() + 1)) }
        assertEquals(HttpStatusCode.PayloadTooLarge, response.status)
    }

    @Test
    fun `database outages return a safe error response`() = testApplication {
        application { configureCookBook { throw SQLException("private connection details", "08006") } }
        val response = client.get("/recipes")
        assertEquals(HttpStatusCode.ServiceUnavailable, response.status)
        assertFalse(response.bodyAsText().contains("private connection details"))
    }
}
