package com.example

import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import java.net.InetAddress
import java.util.concurrent.TimeUnit

internal enum class Ingress(private val clientAddressHeader: String?) {
    DIRECT(null),
    NORTHFLANK("X-Envoy-External-Address"),
    RENDER("CF-Connecting-IP");

    fun rateLimitKey(call: ApplicationCall): String = clientAddressHeader
        ?.let { call.request.headers.getAll(it)?.singleOrNull() }
        ?.takeIf { it.length in 1..45 }
        ?.let(::numericAddress)
        ?: call.request.local.remoteAddress

    companion object {
        private val ipv4 = Regex("[0-9]{1,3}(\\.[0-9]{1,3}){3}")
        private val ipv6Characters = Regex("[0-9a-fA-F:.]+")

        private fun numericAddress(value: String): String? = when {
            ipv4.matches(value) -> value.split('.').map(String::toInt)
                .takeIf { parts -> parts.all { it in 0..255 } }?.joinToString(".")
            ':' in value && ipv6Characters.matches(value) ->
                runCatching { InetAddress.getByName(value).hostAddress }.getOrNull()
            else -> null
        }

        fun fromEnvironment(): Ingress = when (System.getenv("COOKBOOK_INGRESS")) {
            null, "direct" -> DIRECT
            "northflank" -> NORTHFLANK
            "render" -> RENDER
            else -> error("COOKBOOK_INGRESS must be direct, northflank or render")
        }
    }
}

internal class AuthRateLimiter(
    private val attemptsPerMinute: Int,
    private val now: () -> Long = System::nanoTime
) {
    private data class Window(val startedAt: Long, var attempts: Int)

    private val minute = TimeUnit.MINUTES.toNanos(1)
    private val second = TimeUnit.SECONDS.toNanos(1)
    private val windows = mutableMapOf<String, Window>()
    private var cleanedAt = now()

    @Synchronized
    fun retryAfterSeconds(address: String): Long? {
        val time = now()
        if (time - cleanedAt >= minute) {
            windows.values.removeIf { time - it.startedAt >= minute }
            cleanedAt = time
        }
        val window = windows[address]
        if (window == null || time - window.startedAt >= minute) {
            if (window == null && windows.size >= 10_000) return 60
            windows[address] = Window(time, 1)
            return null
        }
        if (window.attempts >= attemptsPerMinute) {
            val remaining = minute - (time - window.startedAt)
            return (remaining + second - 1) / second
        }
        ++window.attempts
        return null
    }
}

internal suspend fun ApplicationCall.checkRateLimit(limiter: AuthRateLimiter, ingress: Ingress): Boolean {
    val retryAfter = limiter.retryAfterSeconds(ingress.rateLimitKey(this)) ?: return true
    response.headers.append(HttpHeaders.RetryAfter, retryAfter.toString())
    sendJson(mapOf("error" to "Za dużo prób. Spróbuj ponownie później."), HttpStatusCode.TooManyRequests)
    return false
}
