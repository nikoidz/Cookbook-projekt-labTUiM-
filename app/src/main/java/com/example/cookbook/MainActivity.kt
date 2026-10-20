
package com.example.cookbook

import android.os.Bundle
import androidx.activity.ComponentActivity
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

// Kolory naszej aplikacji
val Orange = Color(0xFFE8783A)
val Background = Color(0xFFFAF8F4)
val DarkText = Color(0xFF252525)

data class Recipe(
    val id: Int,
    val title: String,
    val preparationTime: String,
    val ingredients: List<String>,
    val steps: List<String>
)

val exampleRecipes = listOf(
    Recipe(
        id = 1,
        title = "Spaghetti Bolognese",
        preparationTime = "30 minut",
        ingredients = listOf(
            "200 g makaronu spaghetti",
            "300 g mięsa mielonego",
            "200 ml sosu pomidorowego",
            "1 cebula",
            "2 ząbki czosnku",
            "Sól i pieprz"
        ),
        steps = listOf(
            "Ugotuj makaron według instrukcji.",
            "Pokrój cebulę i czosnek.",
            "Podsmaż cebulę, czosnek i mięso.",
            "Dodaj sos pomidorowy i przyprawy.",
            "Gotuj sos przez 10 minut.",
            "Podawaj sos z makaronem."
        )
    ),
    Recipe(
        id = 2,
        title = "Domowe naleśniki",
        preparationTime = "25 minut",
        ingredients = listOf(
            "1 szklanka mąki",
            "1 szklanka mleka",
            "2 jajka",
            "Pół szklanki wody",
            "2 łyżki oleju",
            "Szczypta soli"
        ),
        steps = listOf(
            "Wymieszaj mąkę, mleko, jajka i wodę.",
            "Dodaj olej i szczyptę soli.",
            "Wymieszaj ciasto na gładką masę.",
            "Rozgrzej patelnię.",
            "Smaż cienkie naleśniki z obu stron."
        )
    ),
    Recipe(
        id = 3,
        title = "Sałatka grecka",
        preparationTime = "15 minut",
        ingredients = listOf(
            "2 pomidory",
            "1 ogórek",
            "100 g sera feta",
            "10 oliwek",
            "Pół czerwonej cebuli",
            "2 łyżki oliwy"
        ),
        steps = listOf(
            "Umyj wszystkie warzywa.",
            "Pokrój pomidory, ogórka i cebulę.",
            "Pokrój ser feta w kostkę.",
            "Wymieszaj składniki w misce.",
            "Dodaj oliwki i polej oliwą."
        )
    )
)


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)




        setContent {
            var currentScreen by rememberSaveable {
                mutableStateOf("login")
            }

            var selectedRecipeId by rememberSaveable {
                mutableIntStateOf(1)
            }

            var detailsReturnScreen by rememberSaveable {
                mutableStateOf("recipes")
            }

            var addReturnScreen by rememberSaveable {
                mutableStateOf("recipes")
            }

            // Własne przepisy zapisane tymczasowo w pamięci
            val myRecipes = remember {
                mutableStateListOf<Recipe>()
            }


            var apiRecipes by remember {
                mutableStateOf<List<Recipe>>(emptyList())
            }

            var apiStatusMessage by remember {
                mutableStateOf("Ładowanie przepisów...")
            }

// Pobieranie przepisów z mockowanego API
            LaunchedEffect(Unit) {
                ApiClient.getRecipes(
                    onSuccess = { downloadedRecipes ->
                        runOnUiThread {
                            apiRecipes = downloadedRecipes
                            apiStatusMessage = ""
                        }
                    },
                    onError = { error ->
                        runOnUiThread {
                            apiStatusMessage = "Błąd pobierania: $error"
                        }
                    }
                )
            }


            when (currentScreen) {

                "login" -> {
                    LoginScreen(
                        onRegister = {
                            currentScreen = "register"
                        },
                        onLogin = {
                            currentScreen = "recipes"
                        }
                    )
                }

                "register" -> {
                    RegisterScreen(
                        onBackToLogin = {
                            currentScreen = "login"
                        }
                    )
                }


                "recipes" -> {
                    RecipesScreen(
                        recipes = apiRecipes,
                        apiStatusMessage = apiStatusMessage,

                        onLogout = {
                            currentScreen = "login"
                        },
                        onRecipeClick = { recipeId ->
                            selectedRecipeId = recipeId
                            detailsReturnScreen = "recipes"
                            currentScreen = "details"
                        },
                        onAddRecipe = {
                            addReturnScreen = "recipes"
                            currentScreen = "add"
                        },
                        onMyRecipes = {
                            currentScreen = "mine"
                        }
                    )
                }

                "add" -> {
                    AddRecipeScreen(
                        onBack = {
                            currentScreen = addReturnScreen
                        },

                        onSave = { title, minutes, ingredients, steps ->

                            ApiClient.addRecipe(
                                title = title,
                                minutes = minutes,
                                ingredients = ingredients,
                                steps = steps,

                                onSuccess = { newId ->

                                    val newRecipe = Recipe(
                                        id = newId,
                                        title = title,
                                        preparationTime = "$minutes min",
                                        ingredients = ingredients,
                                        steps = steps
                                    )

                                    myRecipes.add(newRecipe)
                                    currentScreen = "mine"
                                },

                                onError = { error ->
                                    android.widget.Toast.makeText(
                                        this@MainActivity,
                                        "Nie udało się dodać przepisu: $error",
                                        android.widget.Toast.LENGTH_LONG
                                    ).show()
                                }
                            )
                        }

                    )
                }




                "edit" -> {
                    val recipe = myRecipes.firstOrNull {
                        it.id == selectedRecipeId
                    }

                    if (recipe != null) {
                        AddRecipeScreen(
                            initialRecipe = recipe,
                            onBack = {
                                currentScreen = "mine"
                            },

                            onSave = { title, minutes, ingredients, steps ->

                                ApiClient.updateRecipe(
                                    recipeId = recipe.id,
                                    title = title,
                                    minutes = minutes,
                                    ingredients = ingredients,
                                    steps = steps,

                                    onSuccess = {
                                        val index = myRecipes.indexOfFirst {
                                            it.id == recipe.id
                                        }

                                        if (index >= 0) {
                                            myRecipes[index] = recipe.copy(
                                                title = title,
                                                preparationTime = "$minutes min",
                                                ingredients = ingredients,
                                                steps = steps
                                            )
                                        }

                                        currentScreen = "mine"
                                    },

                                    onError = { error ->
                                        android.widget.Toast.makeText(
                                            this@MainActivity,
                                            "Nie udało się zapisać zmian: $error",
                                            android.widget.Toast.LENGTH_LONG
                                        ).show()
                                    }
                                )
                            }

                        )
                    }
                }

                "mine" -> {
                    MyRecipesScreen(
                        recipes = myRecipes,
                        onBack = {
                            currentScreen = "recipes"
                        },
                        onAddRecipe = {
                            addReturnScreen = "mine"
                            currentScreen = "add"
                        },
                        onRecipeClick = { recipeId ->
                            selectedRecipeId = recipeId
                            detailsReturnScreen = "mine"
                            currentScreen = "details"
                        },


                        onDeleteRecipe = { recipeId ->

                            ApiClient.deleteRecipe(
                                recipeId = recipeId,

                                onSuccess = {
                                    myRecipes.removeAll { it.id == recipeId }
                                },

                                onError = { error ->
                                    android.widget.Toast.makeText(
                                        this@MainActivity,
                                        "Nie udało się usunąć przepisu: $error",
                                        android.widget.Toast.LENGTH_LONG
                                    ).show()
                                }
                            )
                        },

                        onEditRecipe = { recipeId ->
                            selectedRecipeId = recipeId
                            currentScreen = "edit"
                        }

                    )
                }



                "details" -> {

                    val recipe = (
                            apiRecipes + myRecipes
                            ).firstOrNull {
                            it.id == selectedRecipeId
                        }


                    if (recipe != null) {
                        RecipeDetailsScreen(
                            recipe = recipe,
                            onBack = {
                                currentScreen = detailsReturnScreen
                            }
                        )
                    } else {
                        Text("Nie znaleziono przepisu")
                    }
                }
            }
        }


    }
}

@Composable
fun LoginScreen(
    onRegister: () -> Unit,
    onLogin: () -> Unit
)
{

    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
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
                    message = "Trwa logowanie..."

                    ApiClient.login(
                        email = email.trim(),
                        password = password,
                        onSuccess = {
                            onLogin()
                        },
                        onError = { error ->
                            message = error
                        }
                    )
                }
            },


            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Orange
            )
        ) {
            Text(
                text = "Zaloguj się",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Przycisk rejestracji
        TextButton(
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

        if (message.isNotEmpty()) {
            Text(
                text = message,
                fontSize = 13.sp,
                color = DarkText
            )
        }
    }
}

@Composable
fun RegisterScreen(onBackToLogin: () -> Unit) {

    var username by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
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
                if (
                    username.isBlank() ||
                    email.isBlank() ||
                    password.isBlank()
                ) {
                    message = "Uzupełnij wszystkie pola."
                } else {
                    message = "Trwa rejestracja..."

                    ApiClient.register(
                        username = username.trim(),
                        email = email.trim(),
                        password = password,
                        onSuccess = {
                            message = "Rejestracja testowa zakończona pomyślnie!"
                        },
                        onError = { error ->
                            message = error
                        }
                    )
                }
            },

            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Orange
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(
                text = "Utwórz konto",
                fontSize = 16.sp
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        TextButton(
            onClick = {
                onBackToLogin()
            }
        ) {
            Text(
                text = "Masz już konto? Zaloguj się",
                color = Orange
            )
        }

        if (message.isNotEmpty()) {
            Text(
                text = message,
                color = DarkText,
                fontSize = 13.sp
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}




@Composable
fun RecipesScreen(
    recipes: List<Recipe>,
    apiStatusMessage: String,
    onLogout: () -> Unit,
    onRecipeClick: (Int) -> Unit,
    onAddRecipe: () -> Unit,
    onMyRecipes: () -> Unit
) {





    var searchQuery by rememberSaveable {
        mutableStateOf("")
    }


    val filteredRecipes = recipes.filter { recipe ->
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


        if (apiStatusMessage.isNotEmpty()) {
            Text(
                text = apiStatusMessage,
                color = Color.Gray,
                fontSize = 16.sp
            )
        } else if (filteredRecipes.isEmpty()) {
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
fun AddRecipeScreen(
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

        TextButton(onClick = onBack) {
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
            label = { Text("Nazwa przepisu") },
            placeholder = { Text("Np. Domowa pizza") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = time,
            onValueChange = { time = it },
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
                val minutes = time.toIntOrNull()

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
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Orange
            )
        ) {
            Text(
                if (initialRecipe == null) "Zapisz przepis"
                else "Zapisz zmiany"
            )
        }

        if (error.isNotEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = error,
                color = Color.Red
            )
        }
    }
}



@Composable
fun MyRecipesScreen(
    recipes: List<Recipe>,
    onBack: () -> Unit,
    onAddRecipe: () -> Unit,
    onRecipeClick: (Int) -> Unit,
    onDeleteRecipe: (Int) -> Unit,
    onEditRecipe: (Int) -> Unit
) {


    var searchQuery by rememberSaveable {
        mutableStateOf("")
    }

    val filteredRecipes = recipes.filter { recipe ->
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

        TextButton(onClick = onBack) {
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

        if (filteredRecipes.isEmpty()) {

            Text(

                text = if (recipes.isEmpty()) {
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
                                onClick = {
                                    onDeleteRecipe(recipe.id)
                                }
                            ) {
                                Text(
                                    text = "Usuń",
                                    color = Color.Red
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
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = Orange
            )
        ) {
            Text("Dodaj nowy przepis")
        }
    }
}
