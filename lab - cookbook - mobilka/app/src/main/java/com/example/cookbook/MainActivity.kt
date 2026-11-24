
package com.example.cookbook

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModelProvider

// Kolory naszej aplikacji
val Orange = Color(0xFFE8783A)
val Background = Color(0xFFFAF8F4)
val DarkText = Color(0xFF252525)
private val registrationEmail = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")

data class Recipe(
    val id: Int,
    val title: String,
    val preparationTime: String,
    val ingredients: List<String>,
    val steps: List<String>
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)



        if (
            BuildConfig.FLAVOR == "local" && android.os.Build.VERSION.SDK_INT >= 37 &&
            checkSelfPermission("android.permission.ACCESS_LOCAL_NETWORK") !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(
                arrayOf("android.permission.ACCESS_LOCAL_NETWORK"),
                1001
            )
        }


        ApiClient.initialize(applicationContext)
        val state = ViewModelProvider(this)[CookBookState::class.java]
        setContent {
            BackHandler(
                enabled = state.screen in listOf("register", "mine", "add", "edit", "details", "endingSession"),
                onBack = state::back
            )

            when (state.screen) {
                "session", "endingSession" -> SessionCheckScreen(
                    ending = state.screen == "endingSession",
                    error = state.sessionCheckError.takeIf { state.screen == "session" },
                    onRetry = state::restoreSession,
                    onSignIn = state::discardSavedSession
                )
                "login" -> LoginScreen(
                    busy = state.formBusy,
                    apiMessage = state.formMessage,
                    onRegister = state::openRegistration,
                    onLogin = state::login
                )
                "register" -> RegisterScreen(
                    busy = state.formBusy,
                    accountCreated = state.registrationComplete,
                    apiMessage = state.formMessage,
                    onBackToLogin = state::back,
                    onRegister = state::register
                )
                "recipes" -> RecipesScreen(
                    list = state.publicRecipes,
                    onRetry = state::refreshPublicRecipes,
                    onLogout = state::logout,
                    onRecipeClick = state::openRecipe,
                    onAddRecipe = state::openAddRecipe,
                    onMyRecipes = state::openMyRecipes,
                    onExternalRecipes = state::openExternalRecipes
                )
                "external" -> ExternalRecipesScreen(onBack = state::back)
                "add", "edit" -> {
                    val recipe = state.selectedRecipe.takeIf { state.screen == "edit" }
                    if (state.screen == "edit" && recipe == null) {
                        RecipeUnavailable(onBack = state::back)
                    } else {
                        AddRecipeScreen(
                            initialRecipe = recipe,
                            busy = state.formBusy,
                            apiMessage = state.formMessage,
                            onBack = state::back,
                            onSave = state::saveRecipe
                        )
                    }
                }
                "mine" -> MyRecipesScreen(
                    list = state.myRecipes,
                    deletingRecipeId = state.deletingRecipeId,
                    deleteError = state.deleteError,
                    onRetry = state::refreshMyRecipes,
                    onBack = state::back,
                    onAddRecipe = state::openAddRecipe,
                    onRecipeClick = state::openRecipe,
                    onDeleteRecipe = state::deleteRecipe,
                    onEditRecipe = state::openEditRecipe
                )
                "details" -> {
                    val recipe = state.selectedRecipe
                    if (recipe == null) RecipeUnavailable(onBack = state::back)
                    else RecipeDetailsScreen(recipe = recipe, onBack = state::back)
                }
            }
        }
    }
}

@Composable
private fun SessionCheckScreen(ending: Boolean, error: String?, onRetry: () -> Unit, onSignIn: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().background(Background).statusBarsPadding().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("CookBook", color = Orange, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        if (error == null) {
            LinearProgressIndicator(Modifier.fillMaxWidth(), color = Orange)
            Text(if (ending) "Kończenie sesji…" else "Sprawdzanie zapisanej sesji…", color = DarkText)
        } else {
            Text(error, color = MaterialTheme.colorScheme.error)
            Button(onClick = onRetry, colors = ButtonDefaults.buttonColors(containerColor = Orange)) {
                Text("Spróbuj ponownie")
            }
            TextButton(onClick = onSignIn) {
                Text("Wyczyść sesję i przejdź do logowania", color = Orange)
            }
        }
    }
}

@Composable
private fun RecipeUnavailable(onBack: () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(Background).statusBarsPadding().padding(24.dp)
    ) {
        TextButton(onClick = onBack) { Text("← Wróć", color = Orange) }
        Text("Przepis jest niedostępny.", color = DarkText)
    }
}

@Composable
private fun LoginScreen(
    busy: Boolean,
    apiMessage: String?,
    onRegister: () -> Unit,
    onLogin: (String, String) -> Unit
)
{

    var email by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var message by rememberSaveable { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        Spacer(modifier = Modifier.height(120.dp))

        // Nazwa aplikacji
        Text(
            text = "CookBook",
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            color = Orange
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "Twoje przepisy w jednym miejscu",
            fontSize = 14.sp,
            color = Color.Gray
        )

        Spacer(modifier = Modifier.height(70.dp))

        // Pole e-mail
        Text(
            text = "E-mail",
            color = DarkText,
            fontSize = 14.sp,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            enabled = !busy,
            placeholder = { Text("Wpisz adres e-mail") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email
            ),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Color.White,
                unfocusedContainerColor = Color.White,
                focusedBorderColor = Orange
            )
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Pole hasła
        Text(
            text = "Hasło",
            color = DarkText,
            fontSize = 14.sp,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            enabled = !busy,
            placeholder = { Text("Wpisz hasło") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password
            ),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Color.White,
                unfocusedContainerColor = Color.White,
                focusedBorderColor = Orange
            )
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Przycisk logowania
        Button(


            onClick = {
                if (email.isBlank() || password.isBlank()) {
                    message = "Wpisz e-mail i hasło."
                } else {
                    message = ""
                    onLogin(email.trim(), password)
                }
            },


            enabled = !busy,
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Orange
            )
        ) {
            Text(
                text = if (busy) "Logowanie…" else "Zaloguj się",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Przycisk rejestracji
        TextButton(
            enabled = !busy,
            onClick = {
                onRegister()
            }
        )
        {
            Text(
                text = "Nie masz konta? Zarejestruj się",
                color = Orange
            )
        }

        val feedback = message.ifEmpty { apiMessage.orEmpty() }
        if (feedback.isNotEmpty()) {
            Text(
                text = feedback,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
private fun RegisterScreen(
    busy: Boolean,
    accountCreated: Boolean,
    apiMessage: String?,
    onBackToLogin: () -> Unit,
    onRegister: (String, String, String) -> Unit
) {

    var username by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var message by rememberSaveable { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        Spacer(modifier = Modifier.height(75.dp))

        Text(
            text = "Rejestracja",
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            color = Orange
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "Utwórz swoje konto CookBook",
            fontSize = 14.sp,
            color = Color.Gray
        )

        Spacer(modifier = Modifier.height(45.dp))

        // Nazwa użytkownika
        Text(
            text = "Nazwa użytkownika",
            modifier = Modifier.fillMaxWidth(),
            color = DarkText
        )

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = username,
            onValueChange = { username = it },
            enabled = !busy && !accountCreated,
            placeholder = { Text("Wpisz nazwę użytkownika") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(10.dp)
        )

        Spacer(modifier = Modifier.height(20.dp))

        // E-mail
        Text(
            text = "E-mail",
            modifier = Modifier.fillMaxWidth(),
            color = DarkText
        )

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            enabled = !busy && !accountCreated,
            placeholder = { Text("Wpisz adres e-mail") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email
            ),
            shape = RoundedCornerShape(10.dp)
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Hasło
        Text(
            text = "Hasło",
            modifier = Modifier.fillMaxWidth(),
            color = DarkText
        )

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            enabled = !busy && !accountCreated,
            placeholder = { Text("Wpisz hasło") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password
            ),
            shape = RoundedCornerShape(10.dp)
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Przycisk rejestracji
        Button(

            onClick = {
                message = when {
                    username.trim().length !in 3..80 -> "Nazwa użytkownika musi mieć od 3 do 80 znaków."
                    email.trim().length > 255 || !registrationEmail.matches(email.trim()) -> "Podaj prawidłowy adres e-mail."
                    password.isBlank() || password.length !in 8..128 -> "Podaj hasło o długości od 8 do 128 znaków."
                    else -> ""
                }
                if (message.isEmpty()) onRegister(username.trim(), email.trim(), password)
            },

            enabled = !busy && !accountCreated,
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Orange
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(
                text = if (busy) "Tworzenie konta…" else "Utwórz konto",
                fontSize = 16.sp
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        TextButton(
            enabled = !busy,
            onClick = {
                onBackToLogin()
            }
        ) {
            Text(
                text = "Masz już konto? Zaloguj się",
                color = Orange
            )
        }

        val feedback = message.ifEmpty { apiMessage.orEmpty() }
        if (feedback.isNotEmpty()) {
            Text(
                text = feedback,
                color = if (accountCreated) DarkText else MaterialTheme.colorScheme.error,
                fontSize = 13.sp
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}




@Composable
private fun RecipesScreen(
    list: RecipeListState,
    onRetry: () -> Unit,
    onLogout: () -> Unit,
    onRecipeClick: (Int) -> Unit,
    onAddRecipe: () -> Unit,
    onMyRecipes: () -> Unit,
    onExternalRecipes: () -> Unit
) {





    var searchQuery by rememberSaveable {
        mutableStateOf("")
    }


    val filteredRecipes = list.recipes.filter { recipe ->
        recipe.title.contains(
            searchQuery.trim(),
            ignoreCase = true
        )
    }



    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {

        Spacer(modifier = Modifier.height(24.dp))

        // Nagłówek
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "CookBook",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = Orange
            )

            TextButton(onClick = onLogout) {
                Text(
                    text = "Wyloguj",
                    color = Orange
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Wszystkie przepisy",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = DarkText
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Odkrywaj nowe smaki!",
            fontSize = 14.sp,
            color = Color.Gray
        )

        TextButton(onClick = onExternalRecipes) {
            Text("Przepisy z internetu →", color = Orange)
        }

        Spacer(modifier = Modifier.height(24.dp))


// Wyszukiwarka przepisów
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = {
                Text("Szukaj przepisu...")
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Color.White,
                unfocusedContainerColor = Color.White,
                focusedBorderColor = Orange
            )
        )

        Spacer(modifier = Modifier.height(20.dp))


        RecipeListFeedback(list, onRetry)
        if (!list.loading && list.error == null && filteredRecipes.isEmpty()) {
            Text(
                text = "Nie znaleziono przepisów.",
                color = Color.Gray,
                fontSize = 16.sp
            )
        }


// Lista przepisów
        filteredRecipes.forEach { recipe ->



        Card(
                onClick = {
                    onRecipeClick(recipe.id)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color.White
                )
            ) {


            Column(
                    modifier = Modifier.padding(20.dp)
                ) {

                    Text(
                        text = recipe.title,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = DarkText
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Czas przygotowania: ${recipe.preparationTime}",
                        fontSize = 14.sp,
                        color = Color.Gray
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = onAddRecipe,
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Orange
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("Dodaj przepis")
        }

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedButton(
            onClick = onMyRecipes,
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
        ) {
            Text(
                text = "Moje przepisy",
                color = Orange
            )
        }

    }
}

@Composable
private fun RecipeListFeedback(list: RecipeListState, onRetry: () -> Unit, retryEnabled: Boolean = true) {
    if (list.loading) {
        LinearProgressIndicator(Modifier.fillMaxWidth(), color = Orange)
        Text("Ładowanie przepisów…", color = DarkText, modifier = Modifier.padding(vertical = 8.dp))
    }
    list.error?.let { error ->
        Text(error, color = MaterialTheme.colorScheme.error)
        TextButton(onClick = onRetry, enabled = retryEnabled) {
            Text("Spróbuj ponownie", color = Orange)
        }
    }
}

@Composable
fun RecipeDetailsScreen(
    recipe: Recipe,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {

        // Przycisk powrotu
        TextButton(
            onClick = onBack
        ) {
            Text(
                text = "← Wróć do przepisów",
                color = Orange,
                fontSize = 16.sp
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Nazwa przepisu
        Text(
            text = recipe.title,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = DarkText
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Czas przygotowania
        Text(
            text = "Czas przygotowania: ${recipe.preparationTime}",
            fontSize = 14.sp,
            color = Color.Gray
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Sekcja składników
        Text(
            text = "Składniki",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = Orange
        )

        Spacer(modifier = Modifier.height(16.dp))

        recipe.ingredients.forEach { ingredient ->
            Text(
                text = "• $ingredient",
                fontSize = 16.sp,
                color = DarkText,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Sekcja przygotowania
        Text(
            text = "Sposób przygotowania",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = Orange
        )

        Spacer(modifier = Modifier.height(16.dp))

        recipe.steps.forEachIndexed { index, step ->
            Text(
                text = "${index + 1}. $step",
                fontSize = 16.sp,
                color = DarkText,
                modifier = Modifier.padding(bottom = 12.dp)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}


@Composable
private fun AddRecipeScreen(
    busy: Boolean,
    apiMessage: String?,
    onBack: () -> Unit,
    onSave: (String, Int, List<String>, List<String>) -> Unit,
    initialRecipe: Recipe? = null
) {


    var title by rememberSaveable(initialRecipe?.id) {
        mutableStateOf(initialRecipe?.title ?: "")
    }

    var time by rememberSaveable(initialRecipe?.id) {
        mutableStateOf(
            initialRecipe?.preparationTime?.substringBefore(" ") ?: ""
        )
    }

    var ingredients by rememberSaveable(initialRecipe?.id) {
        mutableStateOf(
            initialRecipe?.ingredients?.joinToString("\n") ?: ""
        )
    }

    var steps by rememberSaveable(initialRecipe?.id) {
        mutableStateOf(
            initialRecipe?.steps?.joinToString("\n") ?: ""
        )
    }

    var error by rememberSaveable { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {

        TextButton(onClick = onBack, enabled = !busy) {
            Text("← Wróć", color = Orange)
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = if (initialRecipe == null) "Dodaj przepis" else "Edytuj przepis",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = Orange
        )

        Spacer(modifier = Modifier.height(24.dp))

        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            enabled = !busy,
            label = { Text("Nazwa przepisu") },
            placeholder = { Text("Np. Domowa pizza") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = time,
            onValueChange = { time = it },
            enabled = !busy,
            label = { Text("Czas przygotowania (min)") },
            placeholder = { Text("Np. 30") },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number
            ),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = ingredients,
            onValueChange = { ingredients = it },
            enabled = !busy,
            label = { Text("Składniki") },
            placeholder = {
                Text("Każdy składnik w nowej linii")
            },
            modifier = Modifier.fillMaxWidth(),
            minLines = 4
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = steps,
            onValueChange = { steps = it },
            enabled = !busy,
            label = { Text("Sposób przygotowania") },
            placeholder = {
                Text("Każdy krok w nowej linii")
            },
            modifier = Modifier.fillMaxWidth(),
            minLines = 5
        )

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = {
                val minutes = time.trim().toIntOrNull()

                val ingredientList = ingredients.lines()
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }

                val stepList = steps.lines()
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }

                if (title.isBlank() ||
                    ingredientList.isEmpty() ||
                    stepList.isEmpty()
                ) {
                    error = "Uzupełnij wszystkie pola."
                } else if (minutes == null || minutes <= 0) {
                    error = "Podaj poprawny czas w minutach."
                } else {
                    error = ""
                    onSave(
                        title.trim(),
                        minutes,
                        ingredientList,
                        stepList
                    )
                }
            },
            enabled = !busy,
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Orange
            )
        ) {
            Text(
                when {
                    busy -> "Zapisywanie…"
                    initialRecipe == null -> "Zapisz przepis"
                    else -> "Zapisz zmiany"
                }
            )
        }

        val feedback = error.ifEmpty { apiMessage.orEmpty() }
        if (feedback.isNotEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = feedback,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}



@Composable
private fun MyRecipesScreen(
    list: RecipeListState,
    deletingRecipeId: Int?,
    deleteError: String?,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    onAddRecipe: () -> Unit,
    onRecipeClick: (Int) -> Unit,
    onDeleteRecipe: (Int) -> Unit,
    onEditRecipe: (Int) -> Unit
) {


    var searchQuery by rememberSaveable {
        mutableStateOf("")
    }

    val filteredRecipes = list.recipes.filter { recipe ->
        recipe.title.contains(
            searchQuery.trim(),
            ignoreCase = true
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {

        TextButton(onClick = onBack, enabled = deletingRecipeId == null) {
            Text("← Wróć", color = Orange)
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Moje przepisy",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = Orange
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Twoja osobista książka kucharska",
            color = Color.Gray,
            fontSize = 14.sp
        )

        Spacer(modifier = Modifier.height(24.dp))


        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = {
                Text("Szukaj w moich przepisach...")
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Color.White,
                unfocusedContainerColor = Color.White,
                focusedBorderColor = Orange
            )
        )

        Spacer(modifier = Modifier.height(20.dp))

        RecipeListFeedback(list, onRetry, retryEnabled = deletingRecipeId == null)
        deleteError?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        if (filteredRecipes.isEmpty() && !list.loading && list.error == null) {

            Text(

                text = if (list.recipes.isEmpty()) {
                    "Nie masz jeszcze własnych przepisów."
                } else {
                    "Nie znaleziono przepisów."
                },

                color = DarkText,
                fontSize = 16.sp
            )
        } else {
            filteredRecipes.forEach { recipe ->

                Card(
                    onClick = {
                        onRecipeClick(recipe.id)
                    },
                    enabled = deletingRecipeId == null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color.White
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp)
                    ) {
                        Text(
                            text = recipe.title,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = DarkText
                        )

                        Spacer(modifier = Modifier.height(8.dp))


                        Text(
                            text = "Czas: ${recipe.preparationTime}",
                            color = Color.Gray
                        )

                        Spacer(modifier = Modifier.height(12.dp))


                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(
                                enabled = deletingRecipeId == null,
                                onClick = {
                                    onEditRecipe(recipe.id)
                                }
                            ) {
                                Text(
                                    text = "Edytuj",
                                    color = Orange
                                )
                            }

                            TextButton(
                                enabled = deletingRecipeId == null,
                                onClick = {
                                    onDeleteRecipe(recipe.id)
                                }
                            ) {
                                Text(
                                    text = if (deletingRecipeId == recipe.id) "Usuwanie…" else "Usuń",
                                    color = if (deletingRecipeId == null) Color.Red else Color.Gray
                                )
                            }
                        }



                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = onAddRecipe,
            enabled = deletingRecipeId == null,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = Orange
            )
        ) {
            Text("Dodaj nowy przepis")
        }
    }
}
