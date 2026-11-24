package com.example.cookbook

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal data class RecipeListState(
    val recipes: List<Recipe> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null
)

internal class CookBookState(application: Application) : AndroidViewModel(application) {
    var screen by mutableStateOf("session")
        private set
    var sessionCheckError by mutableStateOf<String?>(null)
        private set
    var publicRecipes by mutableStateOf(RecipeListState())
        private set
    var myRecipes by mutableStateOf(RecipeListState())
        private set
    var formBusy by mutableStateOf(false)
        private set
    var formMessage by mutableStateOf<String?>(null)
        private set
    var registrationComplete by mutableStateOf(false)
        private set
    var deletingRecipeId by mutableStateOf<Int?>(null)
        private set
    var deleteError by mutableStateOf<String?>(null)
        private set

    private var publicRequestId = 0
    private var mineRequestId = 0
    private var sessionGeneration = 0
    private var selectedRecipeId: Int? = null
    private var returnScreen = "recipes"
    private val sessionStore = SessionStore(
        application,
        origin = BuildConfig.API_BASE_URL.takeIf { BuildConfig.FLAVOR == "cloud" }
    )
    private var checkingSession = false
    private var pendingToken: String? = null

    val selectedRecipe: Recipe?
        get() = (if (returnScreen == "mine" || screen == "edit") myRecipes else publicRecipes)
            .recipes.firstOrNull { it.id == selectedRecipeId }

    init {
        restoreSession()
    }

    fun restoreSession() {
        if (checkingSession || screen != "session") return
        checkingSession = true
        sessionCheckError = null
        ApiClient.sessionToken = null
        viewModelScope.launch {
            val token = withContext(Dispatchers.IO) { runCatching { sessionStore.load() } }
                .getOrElse {
                    checkingSession = false
                    sessionCheckError = "Nie udało się odczytać zapisanej sesji."
                    return@launch
                }
            if (token == null) {
                checkingSession = false
                pendingToken = null
                ApiClient.sessionToken = null
                navigate("login")
                return@launch
            }
            pendingToken = token
            ApiClient.verifySession(token, onSuccess = {
                viewModelScope.launch {
                    checkingSession = false
                    pendingToken = null
                    startSession(token)
                }
            }, onError = { failure ->
                checkingSession = false
                if (failure.sessionExpired) endSession(failure.message)
                else sessionCheckError = failure.message
            })
        }
    }

    fun discardSavedSession() {
        if (!checkingSession && screen == "session") endSession(null, revoke = true)
    }

    private fun startSession(token: String) {
        ApiClient.sessionToken = token
        formBusy = false
        navigate("recipes")
        refreshPublicRecipes()
    }

    fun refreshPublicRecipes() = loadPublicRecipes(replacePending = false)

    private fun loadPublicRecipes(replacePending: Boolean) {
        if (publicRecipes.loading && !replacePending) return
        val requestId = ++publicRequestId
        publicRecipes = publicRecipes.copy(loading = true, error = null)
        ApiClient.getRecipes(
            onSuccess = { if (requestId == publicRequestId) publicRecipes = RecipeListState(it) },
            onError = {
                if (requestId == publicRequestId) {
                    publicRecipes = publicRecipes.copy(loading = false, error = it.message)
                }
            }
        )
    }

    fun refreshMyRecipes() = loadMyRecipes(replacePending = false)

    private fun loadMyRecipes(replacePending: Boolean) {
        if (myRecipes.loading && !replacePending) return
        val requestId = ++mineRequestId
        myRecipes = myRecipes.copy(loading = true, error = null)
        ApiClient.getMyRecipes(
            onSuccess = { if (requestId == mineRequestId) myRecipes = RecipeListState(it) },
            onError = { failure ->
                if (requestId == mineRequestId) {
                    if (failure.sessionExpired) endSession(failure.message)
                    else myRecipes = myRecipes.copy(loading = false, error = failure.message)
                }
            }
        )
    }

    fun openRegistration() {
        if (formBusy) return
        registrationComplete = false
        navigate("register")
    }

    fun login(email: String, password: String) {
        if (formBusy) return
        ++sessionGeneration
        formBusy = true
        formMessage = null
        ApiClient.login(email, password, onSuccess = { token ->
            viewModelScope.launch {
                val saved = withContext(Dispatchers.IO) { runCatching { sessionStore.save(token) }.isSuccess }
                if (saved) {
                    startSession(token)
                } else {
                    pendingToken = token
                    endSession("Nie udało się zapisać sesji na urządzeniu. Spróbuj ponownie.", revoke = true)
                }
            }
        }, onError = ::formFailed)
    }

    fun register(username: String, email: String, password: String) {
        if (formBusy || registrationComplete) return
        formBusy = true
        formMessage = null
        ApiClient.register(username, email, password, onSuccess = {
            formBusy = false
            registrationComplete = true
            formMessage = "Konto utworzone. Możesz się zalogować."
        }, onError = ::formFailed)
    }

    fun openMyRecipes() {
        navigate("mine")
        refreshMyRecipes()
    }

    fun openExternalRecipes() = navigate("external")

    fun openAddRecipe() {
        if (deletingRecipeId != null) return
        returnScreen = screen
        navigate("add")
    }

    fun openRecipe(id: Int) {
        if (deletingRecipeId != null) return
        selectedRecipeId = id
        returnScreen = screen
        navigate("details")
    }

    fun openEditRecipe(id: Int) {
        if (deletingRecipeId != null) return
        selectedRecipeId = id
        navigate("edit")
    }

    fun saveRecipe(title: String, minutes: Int, ingredients: List<String>, steps: List<String>) {
        if (formBusy) return
        val recipeId = if (screen == "edit") selectedRecipeId ?: return else null
        val generation = sessionGeneration
        formBusy = true
        formMessage = null
        val onSuccess = {
            if (generation == sessionGeneration) {
                formBusy = false
                navigate("mine")
                reloadAfterWrite()
            }
        }
        val onError: (ApiFailure) -> Unit = { failure ->
            if (generation == sessionGeneration) formFailed(failure)
        }
        if (recipeId == null) {
            ApiClient.addRecipe(title, minutes, ingredients, steps, { onSuccess() }, onError)
        } else {
            ApiClient.updateRecipe(recipeId, title, minutes, ingredients, steps, onSuccess, onError)
        }
    }

    fun deleteRecipe(id: Int) {
        if (deletingRecipeId != null) return
        val generation = sessionGeneration
        deletingRecipeId = id
        deleteError = null
        ApiClient.deleteRecipe(id, onSuccess = {
            if (generation == sessionGeneration) {
                deletingRecipeId = null
                myRecipes = myRecipes.copy(recipes = myRecipes.recipes.filterNot { it.id == id })
                publicRecipes = publicRecipes.copy(recipes = publicRecipes.recipes.filterNot { it.id == id })
                reloadAfterWrite()
            }
        }, onError = { failure ->
            if (generation == sessionGeneration) {
                deletingRecipeId = null
                if (failure.sessionExpired) endSession(failure.message)
                else deleteError = failure.message
            }
        })
    }

    private fun reloadAfterWrite() {
        loadMyRecipes(replacePending = true)
        loadPublicRecipes(replacePending = true)
    }

    fun back() {
        if (formBusy || deletingRecipeId != null) return
        when (screen) {
            "register" -> navigate("login")
            "external", "mine" -> navigate("recipes")
            "edit" -> navigate("mine")
            "add", "details" -> navigate(returnScreen)
        }
    }

    fun logout() {
        if (screen == "recipes") endSession(null, revoke = true)
    }

    private fun endSession(message: String?, revoke: Boolean = false) {
        val token = ApiClient.sessionToken ?: pendingToken
        ApiClient.sessionToken = null
        pendingToken = null
        val generation = ++sessionGeneration
        ++mineRequestId
        myRecipes = RecipeListState()
        deletingRecipeId = null
        deleteError = null
        formBusy = true
        navigate("endingSession")
        viewModelScope.launch {
            val cleared = withContext(Dispatchers.IO) { runCatching { sessionStore.clear() }.isSuccess }
            formBusy = false
            navigate("login")
            formMessage = if (cleared) message else "Nie udało się usunąć zapisanej sesji z urządzenia."
            if (revoke && token != null) {
                ApiClient.revokeSession(token, onError = {
                    if (screen == "login" && generation == sessionGeneration && message == null && cleared) {
                        formMessage = "Wylogowano z aplikacji. Nie udało się unieważnić sesji na serwerze."
                    }
                })
            }
        }
    }

    private fun formFailed(failure: ApiFailure) {
        formBusy = false
        if (failure.sessionExpired) endSession(failure.message)
        else formMessage = failure.message
    }

    private fun navigate(destination: String) {
        formMessage = null
        if (destination != "mine") deleteError = null
        screen = destination
    }
}
