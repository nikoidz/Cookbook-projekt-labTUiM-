package com.example.cookbook

import android.os.Build
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.junit.runners.model.Statement

@RunWith(AndroidJUnit4::class)
class AuthFormTest {
    @get:Rule(order = 0)
    val networkPermission = TestRule { base, _ ->
        object : Statement() {
            override fun evaluate() {
                if (BuildConfig.FLAVOR == "local" && Build.VERSION.SDK_INT >= 37) {
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
    fun loginRequiresEmailAndPassword() {
        compose.onNodeWithText("Zaloguj się").performScrollTo().performClick()
        compose.onNodeWithText("Wpisz e-mail i hasło.").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun registrationRejectsMalformedEmail() {
        compose.onNodeWithText("Nie masz konta? Zarejestruj się").performScrollTo().performClick()
        compose.onNodeWithText("Wpisz nazwę użytkownika").performTextInput("TestCookBook")
        compose.onNodeWithText("Wpisz adres e-mail").performTextInput("bez-adresu")
        compose.onNodeWithText("Utwórz konto").performScrollTo().performClick()
        compose.onNodeWithText("Podaj prawidłowy adres e-mail.").performScrollTo().assertIsDisplayed()
    }
}
