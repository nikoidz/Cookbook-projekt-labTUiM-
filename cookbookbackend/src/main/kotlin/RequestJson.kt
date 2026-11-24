package com.example

import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import com.google.gson.Strictness
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receiveChannel
import io.ktor.utils.io.readBuffer
import kotlinx.io.readString

internal const val AUTH_BODY_LIMIT = 8 * 1024L
internal const val RECIPE_BODY_LIMIT = 128 * 1024L
private val requestGson = GsonBuilder().setStrictness(Strictness.STRICT).create()

internal suspend fun ApplicationCall.receiveJsonObject(limit: Long): JsonObject? {
    val payload = receiveChannel().readBuffer(limit + 1)
    if (payload.size > limit) {
        sendJson(mapOf("error" to "Przesłane dane są za duże."), HttpStatusCode.PayloadTooLarge)
        return null
    }
    val json = try {
        requestGson.fromJson(payload.readString(), JsonObject::class.java)
    } catch (_: JsonParseException) {
        null
    }
    if (json == null) {
        sendJson(mapOf("error" to "Prześlij poprawny obiekt JSON."), HttpStatusCode.BadRequest)
    }
    return json
}

internal fun JsonObject.stringField(name: String): String? = get(name)?.let {
    if (it.isJsonPrimitive && it.asJsonPrimitive.isString) it.asString else null
}
