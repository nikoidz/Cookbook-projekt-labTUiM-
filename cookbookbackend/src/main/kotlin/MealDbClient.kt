package com.example

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import com.google.gson.JsonParser
import kotlinx.coroutines.future.await
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse.BodyHandlers
import java.net.http.HttpTimeoutException
import java.time.Duration

internal data class ExternalRecipe(
    val id: String,
    val title: String,
    val ingredients: List<String>,
    val instructions: String,
    val imageUrl: String?
)

internal class MealDbClient(
    private val searchEndpoint: URI = URI.create("https://www.themealdb.com/api/json/v1/1/search.php"),
    private val timeoutMillis: Long = 5_000
) {
    private val http = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(3))
        .build()

    suspend fun search(query: String): List<ExternalRecipe> {
        val encoded = URLEncoder.encode(query, Charsets.UTF_8)
        val request = HttpRequest.newBuilder(URI.create("$searchEndpoint?s=$encoded"))
            .header("Accept", "application/json")
            .GET()
            .build()
        val response = withTimeoutOrNull(timeoutMillis) {
            http.sendAsync(request, BodyHandlers.ofString()).await()
        } ?: throw HttpTimeoutException("TheMealDB request timed out")

        if (response.statusCode() != 200) {
            throw IOException("TheMealDB returned HTTP ${response.statusCode()}")
        }

        val meals = JsonParser.parseString(response.body()).asMealObject().get("meals")
            ?: throw JsonParseException("Missing meals field")
        if (meals.isJsonNull) return emptyList()
        if (!meals.isJsonArray) throw JsonParseException("Invalid meals field")

        return meals.asJsonArray.map { item ->
            val meal = item.asMealObject()
            ExternalRecipe(
                id = meal.text("idMeal") ?: throw JsonParseException("Missing meal ID"),
                title = meal.text("strMeal") ?: throw JsonParseException("Missing meal name"),
                ingredients = (1..20).mapNotNull { index ->
                    val ingredient = meal.text("strIngredient$index") ?: return@mapNotNull null
                    listOfNotNull(meal.text("strMeasure$index"), ingredient).joinToString(" ")
                },
                instructions = meal.text("strInstructions").orEmpty(),
                imageUrl = meal.text("strMealThumb")
            )
        }
    }

    private fun JsonElement.asMealObject(): JsonObject =
        if (isJsonObject) asJsonObject else throw JsonParseException("Expected JSON object")

    private fun JsonObject.text(field: String): String? = get(field)
        ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }
        ?.asString
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
}
