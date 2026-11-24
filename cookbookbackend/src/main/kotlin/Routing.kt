package com.example

import com.google.gson.Gson
import com.google.gson.JsonObject
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.sql.Connection
import java.sql.SQLException

private val gson = Gson()

internal suspend fun ApplicationCall.sendJson(data: Any, status: HttpStatusCode = HttpStatusCode.OK) {
    respondText(gson.toJson(data), ContentType.Application.Json, status)
}

private fun String?.recipeText(maxLength: Int): String? =
    this?.trim()?.takeIf { it.length in 1..maxLength && '\u0000' !in it }

private fun JsonObject.recipeLines(name: String, maxLineLength: Int): List<String>? {
    val lines = get(name)?.takeIf { it.isJsonArray }?.asJsonArray ?: return null
    if (lines.size() !in 1..100) return null
    return lines.map { item ->
        if (!item.isJsonPrimitive || !item.asJsonPrimitive.isString) return null
        item.asString.recipeText(maxLineLength) ?: return null
    }
}

private suspend fun ApplicationCall.receiveRecipe(): RecipeInput? {
    val json = receiveJsonObject(RECIPE_BODY_LIMIT) ?: return null
    val title = json.stringField("title").recipeText(200)
    val time = json.stringField("preparationTime").recipeText(64)
    val ingredients = json.recipeLines("ingredients", 250)
    val steps = json.recipeLines("steps", 1_000)
    if (title == null || time == null || ingredients == null || steps == null) {
        sendJson(mapOf("error" to "Niepoprawne dane przepisu."), HttpStatusCode.BadRequest)
        return null
    }
    return RecipeInput(title, time, ingredients, steps)
}

private suspend fun ApplicationCall.recipeId(): Int? {
    val id = parameters["id"]?.toIntOrNull()?.takeIf { it > 0 }
    if (id == null) sendJson(mapOf("error" to "Niepoprawne ID."), HttpStatusCode.BadRequest)
    return id
}

private suspend fun ApplicationCall.recipeNotFound() {
    sendJson(mapOf("error" to "Nie znaleziono przepisu."), HttpStatusCode.NotFound)
}

fun Application.configureRouting() = configureCookBook(Ingress.fromEnvironment(), ::connectToDatabase)

internal fun Application.configureCookBook(ingress: Ingress = Ingress.DIRECT, connect: () -> Connection) {
    val recipes = RecipeStore(connect)
    intercept(ApplicationCallPipeline.Plugins) {
        try {
            proceed()
        } catch (e: SQLException) {
            this@configureCookBook.log.error("Operacja PostgreSQL nie powiodła się (SQLState {}).", e.sqlState)
            val status = if (e.sqlState?.startsWith("08") == true) {
                HttpStatusCode.ServiceUnavailable
            } else {
                HttpStatusCode.InternalServerError
            }
            call.sendJson(mapOf("error" to "Nie udało się wykonać operacji na bazie danych."), status)
        }
    }
    configureAuthRoutes(ingress, connect)

    routing {
        externalRecipeRoutes()

        get("/") {
            call.respondText("CookBook Backend działa!")
        }

        get("/health") {
            call.sendJson(mapOf("status" to "ok"))
        }

        get("/recipes") {
            call.sendJson(recipes.getAll())
        }

        get("/recipes/mine") {
            val userId = call.authenticatedUserId(connect) ?: return@get
            call.sendJson(recipes.getMine(userId))
        }

        get("/recipes/{id}") {
            val id = call.recipeId() ?: return@get
            val recipe = recipes.getById(id)
            if (recipe == null) call.recipeNotFound() else call.sendJson(recipe)
        }

        post("/recipes") {
            val userId = call.authenticatedUserId(connect) ?: return@post
            val input = call.receiveRecipe() ?: return@post
            call.sendJson(recipes.add(input, userId), HttpStatusCode.Created)
        }

        put("/recipes/{id}") {
            val userId = call.authenticatedUserId(connect) ?: return@put
            val id = call.recipeId() ?: return@put
            val input = call.receiveRecipe() ?: return@put
            val updated = recipes.update(id, input, userId)
            if (updated == null) call.recipeNotFound() else call.sendJson(updated)
        }

        delete("/recipes/{id}") {
            val userId = call.authenticatedUserId(connect) ?: return@delete
            val id = call.recipeId() ?: return@delete
            if (recipes.delete(id, userId)) {
                call.sendJson(mapOf("id" to id, "message" to "Przepis usunięty."))
            } else {
                call.recipeNotFound()
            }
        }
    }
}
