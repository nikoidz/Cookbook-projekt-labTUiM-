package com.example.cookbook

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.SocketTimeoutException

class ApiErrorsTest {
    @Test
    fun unauthorizedLoginAndExpiredSessionHaveDifferentEffects() {
        val badLogin = apiFailure(ApiOperation.LOGIN, ApiHttpError(401))
        assertFalse(badLogin.sessionExpired)
        assertEquals("Nieprawidłowy e-mail lub hasło.", badLogin.message)

        for (operation in ApiOperation.entries.filter { it.requiresSession }) {
            assertEquals(expiredSession, apiFailure(operation, ApiHttpError(401)))
        }
    }

    @Test
    fun distinguishesInvalidFormConflictAndRecipePermissionErrors() {
        assertTrue(apiFailure(ApiOperation.REGISTER, ApiHttpError(409)).message.contains("już istnieje"))
        assertTrue(apiFailure(ApiOperation.LOGIN, ApiHttpError(429)).message.startsWith("Za dużo prób"))
        assertTrue(apiFailure(ApiOperation.ADD_RECIPE, ApiHttpError(413)).message.contains("za duże"))
        assertTrue(apiFailure(ApiOperation.ADD_RECIPE, ApiHttpError(400)).message.contains("dane przepisu"))
        for (operation in listOf(ApiOperation.UPDATE_RECIPE, ApiOperation.DELETE_RECIPE)) {
            for (status in listOf(403, 404)) {
                val failure = apiFailure(operation, ApiHttpError(status))
                assertTrue(failure.message.contains("uprawnień"))
                assertFalse(failure.sessionExpired)
            }
        }
    }

    @Test
    fun distinguishesOfflineBackendTimeoutAndUpstreamFailure() {
        val error = IOException("internal host and port")
        assertTrue(apiFailure(ApiOperation.PUBLIC_RECIPES, error, hasNetwork = false).message.startsWith("Brak połączenia"))
        assertTrue(apiFailure(ApiOperation.PUBLIC_RECIPES, error).message.contains("serwerem CookBook"))
        assertTrue(apiFailure(ApiOperation.ADD_RECIPE, SocketTimeoutException()).message.contains("sprawdź", ignoreCase = true))
        assertTrue(apiFailure(ApiOperation.EXTERNAL_SEARCH, ApiHttpError(502)).message.contains("TheMealDB"))
        assertTrue(apiFailure(ApiOperation.EXTERNAL_SEARCH, ApiHttpError(504)).message.contains("na czas"))
    }

    @Test
    fun hidesTechnicalErrorDetailsAndReportsServerFailure() {
        val parseFailure = apiFailure(ApiOperation.MY_RECIPES, IllegalArgumentException("unexpected internal JSON"))
        assertFalse(parseFailure.message.contains("internal JSON"))
        val serverFailure = apiFailure(ApiOperation.UPDATE_RECIPE, ApiHttpError(500))
        assertTrue(serverFailure.message.contains("zapisać zmian"))
        assertTrue(serverFailure.message.contains("serwer", ignoreCase = true))
    }
}
