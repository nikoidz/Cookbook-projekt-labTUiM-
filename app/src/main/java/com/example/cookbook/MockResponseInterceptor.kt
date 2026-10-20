
package com.example.cookbook

import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody


class MockResponseInterceptor : Interceptor {

    private val nextRecipeId =
        java.util.concurrent.atomic.AtomicInteger(1000)


    override fun intercept(chain: Interceptor.Chain): Response {

        val request = chain.request()

        // Przechwytujemy tylko zapytania do testowego API
        if (request.url.host != "mock.cookbook.local") {
            return chain.proceed(request)
        }

        val path = request.url.encodedPath
        val method = request.method

        val (statusCode, jsonResponse) = when {

            // Symulacja logowania
            method == "POST" && path == "/auth/login" -> {
                200 to """
                    {
                        "token": "testowy-token",
                        "user": {
                            "id": 1,
                            "username": "TestUser"
                        }
                    }
                """.trimIndent()
            }

            // Symulacja rejestracji
            method == "POST" && path == "/auth/register" -> {
                201 to """
                    {
                        "id": 1,
                        "username": "TestUser",
                        "message": "Konto testowe utworzone"
                    }
                """.trimIndent()
            }

            // Symulacja pobierania przepisów
            method == "GET" && path == "/recipes" -> {
                200 to """
                    [
                        {
                            "id": 1,
                            "title": "Spaghetti Bolognese",
                            "preparationTime": "30 minut",
                            "ingredients": [
                                "Makaron",
                                "Mięso mielone",
                                "Sos pomidorowy"
                            ],
                            "steps": [
                                "Ugotuj makaron",
                                "Podsmaż mięso",
                                "Dodaj sos pomidorowy"
                            ]
                        },
                        {
                            "id": 2,
                            "title": "Domowe naleśniki",
                            "preparationTime": "25 minut",
                            "ingredients": [
                                "Mąka",
                                "Mleko",
                                "Jajka"
                            ],
                            "steps": [
                                "Wymieszaj składniki",
                                "Rozgrzej patelnię",
                                "Usmaż naleśniki"
                            ]
                        },
                        {
                            "id": 3,
                            "title": "Sałatka grecka",
                            "preparationTime": "15 minut",
                            "ingredients": [
                                "Pomidor",
                                "Ogórek",
                                "Ser feta"
                            ],
                            "steps": [
                                "Pokrój warzywa",
                                "Dodaj ser feta",
                                "Wymieszaj składniki"
                            ]
                        }
                    ]
                """.trimIndent()
            }


// Symulacja dodawania przepisu
            method == "POST" && path == "/recipes" -> {

                val buffer = okio.Buffer()
                request.body?.writeTo(buffer)

                val data = try {
                    org.json.JSONObject(buffer.readUtf8())
                } catch (e: Exception) {
                    null
                }

                if (data == null || data.optString("title").isBlank()) {
                    400 to """
            {"error":"Nieprawidłowe dane przepisu"}
        """.trimIndent()
                } else {
                    val newId = nextRecipeId.getAndIncrement()

                    201 to """
            {
                "id": $newId,
                "message": "Dodano przepis testowy"
            }
        """.trimIndent()
                }
            }




            // Symulacja edytowania przepisu
            method == "PUT" &&
                    path.startsWith("/recipes/") &&
                    path.removePrefix("/recipes/").toIntOrNull() != null -> {

                val recipeId = path.removePrefix("/recipes/").toInt()

                val buffer = okio.Buffer()
                request.body?.writeTo(buffer)

                val data = try {
                    org.json.JSONObject(buffer.readUtf8())
                } catch (e: Exception) {
                    null
                }

                if (data == null || data.optString("title").isBlank()) {
                    400 to """
                        {"error":"Nieprawidłowe dane przepisu"}
                    """.trimIndent()
                } else {
                    200 to """
                        {
                            "id": $recipeId,
                            "message": "Przepis zaktualizowany testowo"
                        }
                    """.trimIndent()
                }
            }



            // Symulacja usuwania przepisu
            method == "DELETE" &&
                    path.startsWith("/recipes/") &&
                    path.removePrefix("/recipes/").toIntOrNull() != null -> {

                val recipeId = path.removePrefix("/recipes/").toInt()

                200 to """
                    {
                        "id": $recipeId,
                        "message": "Przepis usuniety testowo"
                    }
                """.trimIndent()
            }




            // Odpowiedź dla nieznanego zapytania
            else -> {
                404 to """
                    {
                        "error": "Nie znaleziono zasobu"
                    }
                """.trimIndent()
            }
        }

        return Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(statusCode)
            .message(
                if (statusCode in 200..299) "OK"
                else "Not Found"
            )
            .body(
                jsonResponse.toResponseBody(
                    "application/json; charset=utf-8".toMediaType()
                )
            )
            .build()
    }
}
