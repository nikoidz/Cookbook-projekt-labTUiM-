package com.example

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.GeneralSecurityException
import java.security.MessageDigest
import java.security.SecureRandom
import java.sql.Connection
import java.sql.SQLException
import java.util.Base64
import java.util.Locale
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

private val secureRandom = SecureRandom()
private val passwordDispatcher = Dispatchers.Default.limitedParallelism(2)
private const val HASH_ITERATIONS = 600_000
private val emailPattern = Regex("""^[^\s@]+@[^\s@]+\.[^\s@]+$""")
private val bearerPattern = Regex("[A-Za-z0-9_-]{1,128}")
private val AuthNoStore = createRouteScopedPlugin("AuthNoStore") {
    onCall { call -> call.response.headers.append(HttpHeaders.CacheControl, "no-store") }
}
// Keep the password check comparable even if the account does not exist.
private val dummyHash = formattedHash(ByteArray(16), ByteArray(32))

private fun derivePassword(password: String, salt: ByteArray, iterations: Int): ByteArray {
    val characters = password.toCharArray()
    val spec = PBEKeySpec(characters, salt, iterations, 256)
    characters.fill('\u0000')
    return try {
        SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
    } finally {
        spec.clearPassword()
    }
}

private fun formattedHash(salt: ByteArray, hash: ByteArray): String = listOf(
    "pbkdf2_sha256",
    HASH_ITERATIONS.toString(),
    Base64.getEncoder().encodeToString(salt),
    Base64.getEncoder().encodeToString(hash)
).joinToString(":")

private fun hashPassword(password: String): String {
    val salt = ByteArray(16).also(secureRandom::nextBytes)
    return formattedHash(salt, derivePassword(password, salt, HASH_ITERATIONS))
}

private fun verifyPassword(password: String, savedHash: String): Boolean {
    val parts = savedHash.split(":", limit = 4)
    if (parts.size != 4 || parts[0] != "pbkdf2_sha256") return false
    val iterations = parts[1].toIntOrNull() ?: return false
    if (iterations !in 1..1_000_000) return false

    return try {
        val salt = Base64.getDecoder().decode(parts[2])
        val expected = Base64.getDecoder().decode(parts[3])
        salt.size == 16 && expected.size == 32 &&
            MessageDigest.isEqual(derivePassword(password, salt, iterations), expected)
    } catch (_: IllegalArgumentException) {
        false
    } catch (_: GeneralSecurityException) {
        false
    }
}

private fun createToken(): String = Base64.getUrlEncoder().withoutPadding()
    .encodeToString(ByteArray(32).also(secureRandom::nextBytes))

private fun tokenHash(token: String): String = Base64.getUrlEncoder().withoutPadding()
    .encodeToString(MessageDigest.getInstance("SHA-256").digest(token.toByteArray(Charsets.UTF_8)))

private fun ApplicationCall.bearerToken(): String? {
    val authorization = request.headers[HttpHeaders.Authorization] ?: return null
    if (!authorization.startsWith("Bearer ", ignoreCase = true)) return null
    return authorization.substringAfter(' ').trim().takeIf(bearerPattern::matches)
}

private suspend fun deleteSession(hash: String, connect: () -> Connection): Unit = withContext(Dispatchers.IO) {
    connect().use { connection ->
        connection.prepareStatement("DELETE FROM user_sessions WHERE token_hash = ?").use { statement ->
            statement.setString(1, hash)
            statement.executeUpdate()
        }
    }
}

internal fun Route.sessionLogout(revoke: suspend (String) -> Unit) {
    post("/auth/logout") {
        call.response.headers.append(HttpHeaders.CacheControl, "no-store")
        val token = call.bearerToken()
        if (token == null) {
            call.unauthorized()
            return@post
        }
        revoke(tokenHash(token))
        call.respond(HttpStatusCode.NoContent)
    }
}

private suspend fun ApplicationCall.unauthorized() {
    response.headers.append(HttpHeaders.WWWAuthenticate, "Bearer")
    sendJson(mapOf("error" to "Wymagany jest ważny token sesji."), HttpStatusCode.Unauthorized)
}

internal fun Application.configureAuthRoutes(ingress: Ingress, connect: () -> Connection) {
    val loginLimiter = AuthRateLimiter(10)
    val registrationLimiter = AuthRateLimiter(5)

    routing {
        route("/auth") {
            install(AuthNoStore)

            post("/register") {
                if (!call.checkRateLimit(registrationLimiter, ingress)) return@post
                val request = call.receiveJsonObject(AUTH_BODY_LIMIT) ?: return@post
                val username = request.stringField("username")?.trim().orEmpty()
                val email = request.stringField("email")?.trim()?.lowercase(Locale.ROOT).orEmpty()
                val password = request.stringField("password").orEmpty()

                if (username.length !in 3..80 || username.any { it.isISOControl() } ||
                    email.length !in 1..255 || email.any { it.isISOControl() } || !emailPattern.matches(email) ||
                    password.length !in 8..128 || password.isBlank()
                ) {
                    call.sendJson(mapOf("error" to "Niepoprawne dane rejestracji."), HttpStatusCode.BadRequest)
                    return@post
                }

                val passwordHash = withContext(passwordDispatcher) { hashPassword(password) }
                try {
                    val userId = withContext(Dispatchers.IO) {
                        connect().use { connection ->
                            connection.prepareStatement(
                                "INSERT INTO users (username, email, password_hash) VALUES (?, ?, ?) RETURNING id"
                            ).use { statement ->
                                statement.setString(1, username)
                                statement.setString(2, email)
                                statement.setString(3, passwordHash)
                                statement.executeQuery().use { result ->
                                    check(result.next())
                                    result.getInt("id")
                                }
                            }
                        }
                    }
                    call.sendJson(mapOf("id" to userId, "username" to username, "email" to email), HttpStatusCode.Created)
                } catch (e: SQLException) {
                    if (e.sqlState != "23505") throw e
                    call.sendJson(mapOf("error" to "Login lub e-mail jest już zajęty."), HttpStatusCode.Conflict)
                }
            }

            post("/login") {
                if (!call.checkRateLimit(loginLimiter, ingress)) return@post
                val request = call.receiveJsonObject(AUTH_BODY_LIMIT) ?: return@post
                val email = request.stringField("email")?.trim()?.lowercase(Locale.ROOT).orEmpty()
                val password = request.stringField("password").orEmpty()
                if (email.isBlank() || email.length > 255 || email.any { it.isISOControl() } ||
                    password.isBlank() || password.length > 128
                ) {
                    call.sendJson(mapOf("error" to "Sprawdź e-mail i hasło."), HttpStatusCode.BadRequest)
                    return@post
                }

                val user = withContext(Dispatchers.IO) {
                    connect().use { connection ->
                        connection.prepareStatement("SELECT id, username, password_hash FROM users WHERE email = ?").use { statement ->
                            statement.setString(1, email)
                            statement.executeQuery().use { result ->
                                if (result.next()) {
                                    LoginUser(result.getInt("id"), result.getString("username"), result.getString("password_hash"))
                                } else null
                            }
                        }
                    }
                }
                val valid = withContext(passwordDispatcher) { verifyPassword(password, user?.hash ?: dummyHash) }
                if (user == null || !valid) {
                    call.sendJson(mapOf("error" to "Nieprawidłowy e-mail lub hasło."), HttpStatusCode.Unauthorized)
                    return@post
                }

                val token = createToken()
                withContext(Dispatchers.IO) {
                    connect().use { connection ->
                        connection.prepareStatement(
                            "INSERT INTO user_sessions (token_hash, user_id, expires_at) VALUES (?, ?, NOW() + INTERVAL '7 days')"
                        ).use { statement ->
                            statement.setString(1, tokenHash(token))
                            statement.setInt(2, user.id)
                            statement.executeUpdate()
                        }
                    }
                }
                call.sendJson(mapOf("token" to token, "userId" to user.id, "username" to user.username))
            }

            get("/me") {
                val token = call.bearerToken()
                val user = token?.let {
                    withContext(Dispatchers.IO) {
                        connect().use { connection ->
                            connection.prepareStatement(
                                """
                                SELECT u.id, u.username, u.email
                                FROM user_sessions s JOIN users u ON u.id = s.user_id
                                WHERE s.token_hash = ? AND s.expires_at > NOW()
                                """.trimIndent()
                            ).use { statement ->
                                statement.setString(1, tokenHash(token))
                                statement.executeQuery().use { result ->
                                    if (result.next()) {
                                        mapOf("id" to result.getInt("id"), "username" to result.getString("username"), "email" to result.getString("email"))
                                    } else null
                                }
                            }
                        }
                    }
                }
                if (user == null) call.unauthorized() else call.sendJson(user)
            }
        }

        sessionLogout { hash -> deleteSession(hash, connect) }
    }
}

private data class LoginUser(val id: Int, val username: String, val hash: String)

internal suspend fun ApplicationCall.authenticatedUserId(connect: () -> Connection): Int? {
    response.headers.append(HttpHeaders.CacheControl, "no-store")
    val token = bearerToken()
    val userId = token?.let {
        withContext(Dispatchers.IO) {
            connect().use { connection ->
                connection.prepareStatement(
                    "SELECT user_id FROM user_sessions WHERE token_hash = ? AND expires_at > NOW()"
                ).use { statement ->
                    statement.setString(1, tokenHash(token))
                    statement.executeQuery().use { result -> if (result.next()) result.getInt("user_id") else null }
                }
            }
        }
    }
    if (userId == null) unauthorized()
    return userId
}
