
package com.example.cookbook

import android.util.Log
import android.os.Handler
import android.os.Looper
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONArray
import java.io.IOException

object ApiClient {

    // Klient HTTP z mockowanym serwerem
    private val client = OkHttpClient.Builder()
        .addInterceptor(MockResponseInterceptor())
        .build()

    // Pobieranie przepisów z API
    fun getRecipes(
        onSuccess: (List<Recipe>) -> Unit,
        onError: (String) -> Unit
    ) {
        val request = Request.Builder()
            .url("https://mock.cookbook.local/recipes")
            .get()
            .build()

        client.newCall(request).enqueue(object : Callback {

            override fun onFailure(
                call: Call,
                e: IOException
            ) {
                onError(e.message ?: "Błąd połączenia")
            }

            override fun onResponse(
                call: Call,
                response: Response
            ) {
                response.use {

                    Log.d(
                        "CookBookAPI",
                        "Kod HTTP: ${it.code}"
                    )

                    if (!it.isSuccessful) {
                        onError("Błąd HTTP: ${it.code}")
                        return@use
                    }

                    try {
                        val json = it.body?.string()
                            ?: throw IOException("Pusta odpowiedź")

                        val recipes = parseRecipes(json)

                        onSuccess(recipes)

                    } catch (e: Exception) {
                        onError(
                            "Błąd odczytu JSON: ${e.message}"
                        )
                    }
                }
            }
        })
    }

    // Zamiana JSON na listę obiektów Recipe
    private fun parseRecipes(json: String): List<Recipe> {

        val jsonArray = JSONArray(json)

        val recipes = mutableListOf<Recipe>()

        for (i in 0 until jsonArray.length()) {

            val item = jsonArray.getJSONObject(i)

            val ingredientsJson =
                item.getJSONArray("ingredients")

            val stepsJson =
                item.getJSONArray("steps")

            val ingredients = mutableListOf<String>()
            val steps = mutableListOf<String>()

            for (j in 0 until ingredientsJson.length()) {
                ingredients.add(
                    ingredientsJson.getString(j)
                )
            }

            for (j in 0 until stepsJson.length()) {
                steps.add(
                    stepsJson.getString(j)
                )
            }

            val recipe = Recipe(
                id = item.getInt("id"),
                title = item.getString("title"),
                preparationTime = item.getString("preparationTime"),
                ingredients = ingredients,
                steps = steps
            )

            recipes.add(recipe)
        }

        return recipes
    }

    // Tymczasowy test komunikacji
    fun testGetRecipes() {

        getRecipes(
            onSuccess = { recipes ->

                Log.d(
                    "CookBookAPI",
                    "Pobrano ${recipes.size} przepisy"
                )

                recipes.forEach { recipe ->
                    Log.d(
                        "CookBookAPI",
                        "Przepis: ${recipe.title}"
                    )
                }
            },
            onError = { error ->

                Log.e(
                    "CookBookAPI",
                    "Błąd: $error"
                )
            }
        )
    }

    // Logowanie przez symulowane API
    fun login(
        email: String,
        password: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val json = JSONObject()
            .put("email", email)
            .put("password", password)
            .toString()

        val request = Request.Builder()
            .url("https://mock.cookbook.local/auth/login")
            .post(
                json.toRequestBody(
                    "application/json; charset=utf-8".toMediaType()
                )
            )
            .build()

        client.newCall(request).enqueue(object : Callback {

            override fun onFailure(
                call: Call,
                e: IOException
            ) {
                Handler(Looper.getMainLooper()).post {
                    onError(e.message ?: "Błąd połączenia")
                }
            }

            override fun onResponse(
                call: Call,
                response: Response
            ) {
                response.use {

                    Log.d(
                        "CookBookAPI",
                        "Logowanie: HTTP ${it.code}"
                    )

                    val error: String? = try {
                        if (!it.isSuccessful) {
                            "Błąd logowania: HTTP ${it.code}"
                        } else {
                            val body = it.body?.string().orEmpty()
                            val token = JSONObject(body)
                                .optString("token")

                            if (token.isNotBlank()) {
                                null
                            } else {
                                "Brak tokenu w odpowiedzi API"
                            }
                        }
                    } catch (e: Exception) {
                        "Błąd odpowiedzi: ${e.message}"
                    }

                    Handler(Looper.getMainLooper()).post {
                        if (error == null) {
                            onSuccess()
                        } else {
                            onError(error)
                        }
                    }
                }
            }
        })
    }


    // Rejestracja przez symulowane API
    fun register(
        username: String,
        email: String,
        password: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val json = JSONObject()
            .put("username", username)
            .put("email", email)
            .put("password", password)
            .toString()

        val request = Request.Builder()
            .url("https://mock.cookbook.local/auth/register")
            .post(
                json.toRequestBody(
                    "application/json; charset=utf-8".toMediaType()
                )
            )
            .build()

        client.newCall(request).enqueue(object : Callback {

            override fun onFailure(
                call: Call,
                e: IOException
            ) {
                Handler(Looper.getMainLooper()).post {
                    onError(e.message ?: "Błąd połączenia")
                }
            }

            override fun onResponse(
                call: Call,
                response: Response
            ) {
                response.use {

                    Log.d(
                        "CookBookAPI",
                        "Rejestracja: HTTP ${it.code}"
                    )

                    val error: String? = try {
                        if (it.code != 201) {
                            "Błąd rejestracji: HTTP ${it.code}"
                        } else {
                            val body = it.body?.string().orEmpty()
                            val result = JSONObject(body)

                            if (result.optInt("id", -1) > 0) {
                                null
                            } else {
                                "Niepoprawna odpowiedź API"
                            }
                        }
                    } catch (e: Exception) {
                        "Błąd odpowiedzi: ${e.message}"
                    }

                    Handler(Looper.getMainLooper()).post {
                        if (error == null) {
                            onSuccess()
                        } else {
                            onError(error)
                        }
                    }
                }
            }
        })
    }


    // Dodawanie przepisu przez mockowane API
    fun addRecipe(
        title: String,
        minutes: Int,
        ingredients: List<String>,
        steps: List<String>,
        onSuccess: (Int) -> Unit,
        onError: (String) -> Unit
    ) {
        val json = JSONObject()
            .put("title", title)
            .put("preparationTime", "$minutes min")
            .put("ingredients", JSONArray(ingredients))
            .put("steps", JSONArray(steps))
            .toString()

        val request = Request.Builder()
            .url("https://mock.cookbook.local/recipes")
            .post(
                json.toRequestBody(
                    "application/json; charset=utf-8".toMediaType()
                )
            )
            .build()

        client.newCall(request).enqueue(object : Callback {

            override fun onFailure(
                call: Call,
                e: IOException
            ) {
                Handler(Looper.getMainLooper()).post {
                    onError(e.message ?: "Błąd połączenia")
                }
            }

            override fun onResponse(
                call: Call,
                response: Response
            ) {
                response.use {

                    Log.d(
                        "CookBookAPI",
                        "Dodawanie przepisu: HTTP ${it.code}"
                    )

                    val result = runCatching {
                        if (it.code != 201) {
                            throw IOException("HTTP ${it.code}")
                        }

                        val body = it.body?.string().orEmpty()
                        JSONObject(body).getInt("id")
                    }

                    Handler(Looper.getMainLooper()).post {
                        result.fold(
                            onSuccess = { newId ->
                                onSuccess(newId)
                            },
                            onFailure = { error ->
                                onError(
                                    error.message
                                        ?: "Błąd dodawania przepisu"
                                )
                            }
                        )
                    }
                }
            }
        })
    }


    // Edytowanie przepisu przez symulowane API
    fun updateRecipe(
        recipeId: Int,
        title: String,
        minutes: Int,
        ingredients: List<String>,
        steps: List<String>,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val json = JSONObject()
            .put("title", title)
            .put("preparationTime", "$minutes min")
            .put("ingredients", JSONArray(ingredients))
            .put("steps", JSONArray(steps))
            .toString()

        val request = Request.Builder()
            .url("https://mock.cookbook.local/recipes/$recipeId")
            .put(
                json.toRequestBody(
                    "application/json; charset=utf-8".toMediaType()
                )
            )
            .build()

        client.newCall(request).enqueue(object : Callback {

            override fun onFailure(
                call: Call,
                e: IOException
            ) {
                Handler(Looper.getMainLooper()).post {
                    onError(e.message ?: "Błąd połączenia")
                }
            }

            override fun onResponse(
                call: Call,
                response: Response
            ) {
                response.use {

                    Log.d(
                        "CookBookAPI",
                        "Edytowanie przepisu: HTTP ${it.code}"
                    )

                    val error: String? = try {
                        if (it.code != 200) {
                            "Błąd edytowania: HTTP ${it.code}"
                        } else {
                            val body = it.body?.string().orEmpty()
                            val result = JSONObject(body)

                            if (result.optInt("id", -1) == recipeId) {
                                null
                            } else {
                                "Nieprawidłowe ID w odpowiedzi API"
                            }
                        }
                    } catch (e: Exception) {
                        "Błąd odpowiedzi: ${e.message}"
                    }

                    Handler(Looper.getMainLooper()).post {
                        if (error == null) {
                            onSuccess()
                        } else {
                            onError(error)
                        }
                    }
                }
            }
        })
    }


    // Usuwanie przepisu przez symulowane API
    fun deleteRecipe(
        recipeId: Int,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val request = Request.Builder()
            .url("https://mock.cookbook.local/recipes/$recipeId")
            .delete()
            .build()

        client.newCall(request).enqueue(object : Callback {

            override fun onFailure(
                call: Call,
                e: IOException
            ) {
                Handler(Looper.getMainLooper()).post {
                    onError(e.message ?: "Błąd połączenia")
                }
            }

            override fun onResponse(
                call: Call,
                response: Response
            ) {
                response.use {

                    Log.d(
                        "CookBookAPI",
                        "Usuwanie przepisu: HTTP ${it.code}"
                    )

                    val error: String? = try {
                        if (it.code != 200) {
                            "Błąd usuwania: HTTP ${it.code}"
                        } else {
                            val body = it.body?.string().orEmpty()
                            val result = JSONObject(body)

                            if (result.optInt("id", -1) == recipeId) {
                                null
                            } else {
                                "Nieprawidłowe ID przepisu"
                            }
                        }
                    } catch (e: Exception) {
                        "Błąd odpowiedzi: ${e.message}"
                    }

                    Handler(Looper.getMainLooper()).post {
                        if (error == null) {
                            onSuccess()
                        } else {
                            onError(error)
                        }
                    }
                }
            }
        })
    }

}
