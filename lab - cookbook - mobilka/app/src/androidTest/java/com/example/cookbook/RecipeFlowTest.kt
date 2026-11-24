package com.example.cookbook

import android.os.Build
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.UUID
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.junit.runners.model.Statement

@RunWith(AndroidJUnit4::class)
class RecipeFlowTest {
    @get:Rule(order = 0)
    val disposableBackend = TestRule { base, _ ->
        object : Statement() {
            override fun evaluate() {
                assumeTrue(
                    BuildConfig.FLAVOR == "local" &&
                        InstrumentationRegistry.getArguments().getString("cookbookDisposableBackend") == "true"
                )
                if (Build.VERSION.SDK_INT >= 37) {
                    val instrumentation = InstrumentationRegistry.getInstrumentation()
                    instrumentation.uiAutomation.grantRuntimePermission(
                        instrumentation.targetContext.packageName,
                        "android.permission.ACCESS_LOCAL_NETWORK"
                    )
                }
                base.evaluate()
            }
        }
    }

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun accountCanSaveFindEditAndRemoveARecipe() {
        val suffix = UUID.randomUUID().toString().take(8)
        val email = "cookbook-$suffix@example.test"
        val password = UUID.randomUUID().toString()
        val title = "Zupa $suffix"
        val updatedTitle = "Zupa pomidorowa $suffix"

        tap("Nie masz konta? Zarejestruj się")
        fill("Wpisz nazwę użytkownika", "cookbook-$suffix")
        fill("Wpisz adres e-mail", email)
        fill("Wpisz hasło", password)
        tap("Utwórz konto")
        await("Konto utworzone. Możesz się zalogować.")
        tap("Masz już konto? Zaloguj się")
        fill("Wpisz adres e-mail", email)
        fill("Wpisz hasło", password)
        tap("Zaloguj się")
        await("Wszystkie przepisy")

        tap("Dodaj przepis")
        fill("Nazwa przepisu", title)
        fill("Czas przygotowania (min)", "20")
        fill("Składniki", "Pomidor")
        fill("Sposób przygotowania", "Pokrój pomidor")
        tap("Zapisz przepis")
        await(title)
        tap("← Wróć")

        fill("Szukaj przepisu...", "brak-$suffix")
        await("Nie znaleziono przepisów.")
        compose.onNodeWithText("brak-$suffix").performTextReplacement(title)
        compose.onNode(hasText(title) and !hasSetTextAction()).performScrollTo().performClick()
        await("• Pomidor")
        tap("← Wróć do przepisów")
        tap("Przepisy z internetu →")
        compose.onNodeWithText("Źródło przepisów i zdjęć: TheMealDB").assertIsDisplayed()
        compose.onNodeWithText("Nazwa potrawy").performTextInput("Arrabiata")
        compose.onNodeWithText("Szukaj").performClick()
        await("Spicy Arrabiata Penne")
        compose.onNodeWithText("← Wróć").performClick()
        tap("Moje przepisy")
        await(title)
        tap("Edytuj")
        compose.onNodeWithText(title).performScrollTo().performTextReplacement(updatedTitle)
        tap("Zapisz zmiany")
        await(updatedTitle)

        compose.activityRule.scenario.close()
        ActivityScenario.launch(MainActivity::class.java).use {
            await("Wszystkie przepisy")
            tap("Moje przepisy")
            await(updatedTitle)
            tap("Usuń")
            await("Nie masz jeszcze własnych przepisów.")
            tap("← Wróć")
            tap("Wyloguj")
            await("Zaloguj się")
        }
    }

    @Test
    fun loginReportsTheConnectionProblem() {
        val networkState = InstrumentationRegistry.getArguments().getString("cookbookNetworkState")
        assumeTrue(networkState == "offline" || networkState == "backendStopped")
        val expectedMessage = if (networkState == "offline") {
            "Brak połączenia z siecią. Sprawdź Wi-Fi lub dane komórkowe."
        } else {
            "Nie udało się połączyć z serwerem CookBook. Sprawdź połączenie."
        }
        fill("Wpisz adres e-mail", "cookbook-network@example.test")
        fill("Wpisz hasło", UUID.randomUUID().toString())
        tap("Zaloguj się")
        await(expectedMessage)
    }

    private fun tap(text: String) {
        compose.onNodeWithText(text).performScrollTo().performClick()
    }

    private fun fill(label: String, value: String) {
        compose.onNodeWithText(label).performScrollTo().performTextInput(value)
    }

    private fun await(text: String) {
        compose.waitUntil(15_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText(text).performScrollTo().assertIsDisplayed()
    }
}
