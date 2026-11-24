package com.example

import com.google.gson.JsonParseException
import io.ktor.http.HttpStatusCode
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import java.io.IOException
import java.net.http.HttpTimeoutException

internal fun Route.externalRecipeRoutes(mealDb: MealDbClient = MealDbClient()) {
    get("/external/recipes") {
        val values = call.request.queryParameters.getAll("search")
        val query = values?.singleOrNull()?.trim().orEmpty()
        if (query.length !in 1..100 || query.any { Character.isISOControl(it) }) {
            call.sendJson(
                mapOf("error" to "Podaj jedną nazwę potrawy o długości od 1 do 100 znaków."),
                HttpStatusCode.BadRequest
            )
            return@get
        }

        val recipes = try {
            mealDb.search(query)
        } catch (_: HttpTimeoutException) {
            call.sendJson(
                mapOf("error" to "TheMealDB nie odpowiedziało na czas. Spróbuj ponownie."),
                HttpStatusCode.GatewayTimeout
            )
            return@get
        } catch (_: IOException) {
            null
        } catch (_: JsonParseException) {
            null
        }

        if (recipes == null) {
            call.sendJson(
                mapOf("error" to "TheMealDB jest chwilowo niedostępne. Spróbuj ponownie później."),
                HttpStatusCode.BadGateway
            )
        } else {
            call.sendJson(recipes)
        }
    }
}
