package com.example

import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import java.security.MessageDigest
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SessionLogoutTest {
    @Test
    fun `application rejects logout without a bearer token`() = testApplication {
        configure()
        for (headerValue in listOf(null, "Basic dGVzdA==", "Bearer")) {
            val response = client.post("/auth/logout") {
                headerValue?.let { header(HttpHeaders.Authorization, it) }
            }
            assertEquals(HttpStatusCode.Unauthorized, response.status)
        }
    }

    @Test
    fun `logout removes only the supplied token hash and can be repeated`() = testApplication {
        val testToken = "test-session-token"
        val tokenHash = Base64.getUrlEncoder().withoutPadding().encodeToString(
            MessageDigest.getInstance("SHA-256").digest(testToken.toByteArray())
        )
        val sessions = mutableSetOf(tokenHash, "another-session-hash")
        application {
            routing {
                sessionLogout { hash -> sessions.remove(hash) }
            }
        }

        repeat(2) {
            val response = client.post("/auth/logout") {
                header(HttpHeaders.Authorization, "Bearer $testToken")
            }
            assertEquals(HttpStatusCode.NoContent, response.status)
            assertTrue(response.bodyAsText().isEmpty())
        }
        assertEquals(setOf("another-session-hash"), sessions)
    }
}
