package com.example.cookbook

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.ConnectivityManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.io.InterruptedIOException
import java.util.concurrent.TimeUnit

internal object ApiClient {

    private val baseUrl = BuildConfig.API_BASE_URL
    private val jsonType = "application/json; charset=utf-8".toMediaType()
    private val client = OkHttpClient.Builder()
        .callTimeout(15, TimeUnit.SECONDS)
        .build()
    private val writeClient = client.newBuilder()
        .retryOnConnectionFailure(false)
        .build()
    private val mainHandler = Handler(Looper.getMainLooper())
    private var connectivity: ConnectivityManager? = null

    fun initialize(context: Context) {
        connectivity = context.getSystemService(ConnectivityManager::class.java)
    }

    @Volatile
    var sessionToken: String? = null

    private fun newApiCall(request: Request): Call =
        (if (request.method == "GET") client else writeClient).newCall(request)

    // Wszystkie odpowiedzi trafiają na główny wątek Androida.
    private fun <T> execute(
        operation: ApiOperation,
        request: Request,
        parseSuccess: (String) -> T,
        onSuccess: (T) -> Unit,
        onError: (ApiFailure) -> Unit
    ): Call = newApiCall(request).apply {
        fun reportFailure(error: Exception) {
            val hasNetwork = connectivity?.let { it.activeNetwork != null } ?: true
            val failure = apiFailure(operation, error, hasNetwork)
            mainHandler.post {
                if (!isCanceled() || error is InterruptedIOException) onError(failure)
            }
        }

        enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                reportFailure(e)
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    val result = response.use { res ->
                        Log.d("CookBookAPI", "${request.method} ${request.url.encodedPath}: HTTP ${res.code}")
                        if (!res.isSuccessful) throw ApiHttpError(res.code)
                        parseSuccess(res.body.string())
                    }
                    mainHandler.post {
                        if (!call.isCanceled()) onSuccess(result)
                    }
                } catch (e: Exception) {
                    reportFailure(e)
                }
            }
        })
    }

    private fun authenticatedRequest(path: String): Request.Builder? =
        sessionToken?.let { requestWithToken(path, it) }

    private fun requestWithToken(path: String, token: String): Request.Builder =
        Request.Builder()
            .url("$baseUrl$path")
            .header("Authorization", "Bearer $token")

    private fun recipeFromJson(item: JSONObject): Recipe {
        val ingredients = item.getJSONArray("ingredients")
        val steps = item.getJSONArray("steps")
        return Recipe(
            id = item.getInt("id"),
            title = item.getString("title"),
            preparationTime = item.getString("preparationTime"),
            ingredients = List(ingredients.length()) { ingredients.getString(it) },
            steps = List(steps.length()) { steps.getString(it) }
        )
    }

    private fun recipesFromJson(json: String): List<Recipe> {
        val array = JSONArray(json)
        return List(array.length()) { recipeFromJson(array.getJSONObject(it)) }
    }

    fun getRecipes(
        onSuccess: (List<Recipe>) -> Unit,
        onError: (ApiFailure) -> Unit
    ) {
        val request = Request.Builder().url("$baseUrl/recipes").get().build()
        execute(ApiOperation.PUBLIC_RECIPES, request, ::recipesFromJson, onSuccess, onError)
    }

    fun searchExternalRecipes(
        query: String,
        onSuccess: (List<ExternalRecipe>) -> Unit,
        onError: (String) -> Unit
    ): Call {
        val url = "$baseUrl/external/recipes".toHttpUrl().newBuilder()
            .addQueryParameter("search", query)
            .build()
        return execute(
            operation = ApiOperation.EXTERNAL_SEARCH,
            request = Request.Builder().url(url).build(),
            parseSuccess = { body ->
                val recipes = JSONArray(body)
                List(recipes.length()) { index ->
                    val item = recipes.getJSONObject(index)
                    val ingredients = item.getJSONArray("ingredients")
                    ExternalRecipe(
                        id = item.getString("id"),
                        title = item.getString("title"),
                        ingredients = List(ingredients.length()) { ingredients.getString(it) },
                        instructions = item.getString("instructions"),
                        imageUrl = if (item.isNull("imageUrl")) null else item.getString("imageUrl")
                    )
                }
            },
            onSuccess = onSuccess,
            onError = { onError(it.message) }
        )
    }

    suspend fun loadExternalRecipeImage(imageUrl: String): Bitmap? = withContext(Dispatchers.IO) {
        val url = imageUrl.toHttpUrlOrNull()
            ?.takeIf { it.isHttps && it.host == "www.themealdb.com" }
            ?: return@withContext null
        try {
            val call = client.newCall(Request.Builder().url(url).build())
            call.timeout().timeout(8, TimeUnit.SECONDS)
            call.execute().use { response ->
                if (response.isSuccessful) BitmapFactory.decodeStream(response.body.byteStream())
                else null
            }
        } catch (_: IOException) {
            null
        }
    }

    fun getMyRecipes(
        onSuccess: (List<Recipe>) -> Unit,
        onError: (ApiFailure) -> Unit
    ) {
        val builder = authenticatedRequest("/recipes/mine")
            ?: return onError(expiredSession)
        execute(ApiOperation.MY_RECIPES, builder.get().build(), ::recipesFromJson, onSuccess, onError)
    }

    fun login(
        email: String,
        password: String,
        onSuccess: (String) -> Unit,
        onError: (ApiFailure) -> Unit
    ) {
        val json = JSONObject()
            .put("email", email)
            .put("password", password)
            .toString()
        val request = Request.Builder()
            .url("$baseUrl/auth/login")
            .post(json.toRequestBody(jsonType))
            .build()

        execute(
            operation = ApiOperation.LOGIN,
            request,
            parseSuccess = { body ->
                JSONObject(body).getString("token").also { check(it.isNotBlank()) }
            },
            onSuccess = onSuccess,
            onError = onError
        )
    }

    fun verifySession(token: String, onSuccess: () -> Unit, onError: (ApiFailure) -> Unit) {
        val request = requestWithToken("/auth/me", token).get().build()
        execute(
            ApiOperation.VERIFY_SESSION,
            request,
            parseSuccess = { body -> check(JSONObject(body).getInt("id") > 0) },
            onSuccess = { onSuccess() },
            onError = onError
        )
    }

    fun revokeSession(token: String, onError: (ApiFailure) -> Unit) {
        val request = requestWithToken("/auth/logout", token).post(ByteArray(0).toRequestBody()).build()
        execute(ApiOperation.LOGOUT, request, parseSuccess = { Unit }, onSuccess = {}, onError = onError)
    }

    fun register(
        username: String,
        email: String,
        password: String,
        onSuccess: () -> Unit,
        onError: (ApiFailure) -> Unit
    ) {
        val json = JSONObject()
            .put("username", username)
            .put("email", email)
            .put("password", password)
            .toString()
        val request = Request.Builder()
            .url("$baseUrl/auth/register")
            .post(json.toRequestBody(jsonType))
            .build()

        execute(
            operation = ApiOperation.REGISTER,
            request,
            parseSuccess = { body ->
                val id = JSONObject(body).getInt("id")
                check(id > 0)
                id
            },
            onSuccess = { onSuccess() },
            onError = onError
        )
    }

    private fun recipeJson(
        title: String,
        minutes: Int,
        ingredients: List<String>,
        steps: List<String>
    ): String = JSONObject()
        .put("title", title)
        .put("preparationTime", "$minutes min")
        .put("ingredients", JSONArray(ingredients))
        .put("steps", JSONArray(steps))
        .toString()

    fun addRecipe(
        title: String,
        minutes: Int,
        ingredients: List<String>,
        steps: List<String>,
        onSuccess: (Int) -> Unit,
        onError: (ApiFailure) -> Unit
    ) {
        val builder = authenticatedRequest("/recipes")
            ?: return onError(expiredSession)
        val request = builder.post(
            recipeJson(title, minutes, ingredients, steps).toRequestBody(jsonType)
        ).build()
        execute(ApiOperation.ADD_RECIPE, request, { JSONObject(it).getInt("id").also { id -> check(id > 0) } }, onSuccess, onError)
    }

    fun updateRecipe(
        recipeId: Int,
        title: String,
        minutes: Int,
        ingredients: List<String>,
        steps: List<String>,
        onSuccess: () -> Unit,
        onError: (ApiFailure) -> Unit
    ) {
        val builder = authenticatedRequest("/recipes/$recipeId")
            ?: return onError(expiredSession)
        val request = builder.put(
            recipeJson(title, minutes, ingredients, steps).toRequestBody(jsonType)
        ).build()
        execute(
            operation = ApiOperation.UPDATE_RECIPE,
            request,
            parseSuccess = { body ->
                check(JSONObject(body).getInt("id") == recipeId)
            },
            onSuccess = { onSuccess() },
            onError = onError
        )
    }

    fun deleteRecipe(
        recipeId: Int,
        onSuccess: () -> Unit,
        onError: (ApiFailure) -> Unit
    ) {
        val builder = authenticatedRequest("/recipes/$recipeId")
            ?: return onError(expiredSession)
        execute(
            operation = ApiOperation.DELETE_RECIPE,
            builder.delete().build(),
            parseSuccess = { body ->
                check(JSONObject(body).getInt("id") == recipeId)
            },
            onSuccess = { onSuccess() },
            onError = onError
        )
    }
}
