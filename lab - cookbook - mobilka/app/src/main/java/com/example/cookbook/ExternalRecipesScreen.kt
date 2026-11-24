package com.example.cookbook

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import okhttp3.Call

data class ExternalRecipe(
    val id: String,
    val title: String,
    val ingredients: List<String>,
    val instructions: String,
    val imageUrl: String?
)

@Composable
internal fun ExternalRecipesScreen(onBack: () -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var results by remember { mutableStateOf<List<ExternalRecipe>?>(null) }
    var selectedRecipe by remember { mutableStateOf<ExternalRecipe?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var pendingSearch by remember { mutableStateOf<Call?>(null) }
    val listState = rememberLazyListState()
    val focus = LocalFocusManager.current
    val term = query.trim()

    DisposableEffect(Unit) {
        onDispose { pendingSearch?.cancel() }
    }

    val closeDetails = { selectedRecipe = null }
    BackHandler { if (selectedRecipe == null) onBack() else closeDetails() }

    val recipe = selectedRecipe
    if (recipe != null) {
        ExternalRecipeDetails(recipe, onBack = closeDetails)
        return
    }

    Column(
        modifier = externalScreenModifier().imePadding().padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        TextButton(onClick = onBack) {
            Text("← Wróć", color = Orange)
        }
        Text("Przepisy z internetu", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Orange)
        Text("Źródło przepisów i zdjęć: TheMealDB", color = Color.Gray, fontSize = 14.sp)
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Nazwa potrawy") },
            placeholder = { Text("Np. chicken") },
            singleLine = true,
            isError = term.length > 100,
            supportingText = if (term.length > 100) {
                { Text("Wpisz maksymalnie 100 znaków.") }
            } else null,
            enabled = pendingSearch == null,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Color.White,
                unfocusedContainerColor = Color.White,
                focusedBorderColor = Orange
            )
        )
        Button(
            onClick = {
                if (pendingSearch == null) {
                    focus.clearFocus()
                    results = null
                    error = null
                    pendingSearch = ApiClient.searchExternalRecipes(
                        term,
                        onSuccess = {
                            results = it
                            pendingSearch = null
                        },
                        onError = {
                            error = it
                            pendingSearch = null
                        }
                    )
                }
            },
            enabled = pendingSearch == null && term.length in 1..100,
            modifier = Modifier.fillMaxWidth().height(54.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Orange)
        ) {
            Text("Szukaj")
        }

        val recipes = results
        when {
            pendingSearch != null -> {
                LinearProgressIndicator(Modifier.fillMaxWidth(), color = Orange)
                Text("Wyszukiwanie przepisów…", color = DarkText)
            }
            error != null -> Text(error.orEmpty(), color = MaterialTheme.colorScheme.error)
            recipes?.isEmpty() == true -> Text("Nie znaleziono przepisów.", color = DarkText)
            recipes != null -> LazyColumn(
                state = listState,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                items(recipes) { item ->
                    Card(
                        onClick = { selectedRecipe = item },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White)
                    ) {
                        Text(
                            item.title,
                            modifier = Modifier.padding(20.dp),
                            color = DarkText,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ExternalRecipeDetails(recipe: ExternalRecipe, onBack: () -> Unit) {
    Column(
        modifier = externalScreenModifier()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        TextButton(onClick = onBack) {
            Text("← Wróć do wyników", color = Orange)
        }
        Text(recipe.title, fontSize = 28.sp, fontWeight = FontWeight.Bold, color = DarkText)
        recipe.imageUrl?.let { url ->
            val photo by produceState<ImageBitmap?>(null, url) {
                value = ApiClient.loadExternalRecipeImage(url)?.asImageBitmap()
            }
            photo?.let { bitmap ->
                Image(
                    bitmap = bitmap,
                    contentDescription = "Zdjęcie potrawy ${recipe.title}",
                    modifier = Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Fit
                )
            }
        }
        Text("Źródło przepisów i zdjęć: TheMealDB", color = Color.Gray, fontSize = 14.sp)
        Text("Składniki", color = Orange, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        if (recipe.ingredients.isEmpty()) {
            Text("Brak listy składników.", color = DarkText)
        }
        recipe.ingredients.forEach { ingredient ->
            Text("• $ingredient", color = DarkText, fontSize = 16.sp)
        }
        Text("Sposób przygotowania", color = Orange, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text(
            recipe.instructions.ifBlank { "Brak instrukcji przygotowania." },
            color = DarkText,
            fontSize = 16.sp
        )
        Spacer(Modifier.height(12.dp))
    }
}

private fun externalScreenModifier() = Modifier
    .fillMaxSize()
    .background(Background)
    .statusBarsPadding()
    .navigationBarsPadding()
