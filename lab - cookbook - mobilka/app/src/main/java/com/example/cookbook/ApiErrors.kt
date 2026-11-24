package com.example.cookbook

import java.io.IOException
import java.io.InterruptedIOException

internal enum class ApiOperation(
    val failureMessage: String,
    val requiresSession: Boolean = false,
    val changesData: Boolean = false
) {
    PUBLIC_RECIPES("Nie udało się pobrać przepisów."),
    MY_RECIPES("Nie udało się pobrać Twoich przepisów.", requiresSession = true),
    LOGIN("Nie udało się zalogować."),
    REGISTER("Nie udało się utworzyć konta.", changesData = true),
    VERIFY_SESSION("Nie udało się sprawdzić sesji.", requiresSession = true),
    LOGOUT("Nie udało się unieważnić sesji na serwerze."),
    ADD_RECIPE("Nie udało się dodać przepisu.", requiresSession = true, changesData = true),
    UPDATE_RECIPE("Nie udało się zapisać zmian.", requiresSession = true, changesData = true),
    DELETE_RECIPE("Nie udało się usunąć przepisu.", requiresSession = true, changesData = true),
    EXTERNAL_SEARCH("Nie udało się wyszukać przepisów.")
}

internal data class ApiFailure(val message: String, val sessionExpired: Boolean = false)

internal class ApiHttpError(val status: Int) : IOException()

internal val expiredSession = ApiFailure("Sesja wygasła. Zaloguj się ponownie.", sessionExpired = true)

internal fun apiFailure(operation: ApiOperation, error: Exception, hasNetwork: Boolean = true): ApiFailure {
    if (error is ApiHttpError && error.status == 401 && operation.requiresSession) {
        return expiredSession
    }
    val message = when (error) {
        is ApiHttpError -> httpErrorMessage(operation, error.status)
        is InterruptedIOException -> if (operation.changesData) {
            "Serwer nie odpowiedział na czas. Sprawdź, czy operacja się udała, zanim spróbujesz ponownie."
        } else {
            "Serwer nie odpowiedział na czas. Spróbuj ponownie."
        }
        is IOException -> if (hasNetwork) {
            "Nie udało się połączyć z serwerem CookBook. Sprawdź połączenie."
        } else {
            "Brak połączenia z siecią. Sprawdź Wi-Fi lub dane komórkowe."
        }
        else -> "${operation.failureMessage} Serwer zwrócił nieoczekiwaną odpowiedź."
    }
    return ApiFailure(message)
}

private fun httpErrorMessage(operation: ApiOperation, status: Int): String = when {
    status == 401 && operation == ApiOperation.LOGIN -> "Nieprawidłowy e-mail lub hasło."
    status == 409 && operation == ApiOperation.REGISTER -> "Konto z tym e-mailem lub nazwą użytkownika już istnieje."
    status == 429 -> "Za dużo prób. Odczekaj chwilę i spróbuj ponownie."
    status == 413 -> "Przesłane dane są za duże. Skróć wpisany tekst i spróbuj ponownie."
    status == 400 -> when (operation) {
        ApiOperation.LOGIN -> "Sprawdź podany e-mail i hasło."
        ApiOperation.REGISTER -> "Sprawdź dane rejestracji i spróbuj ponownie."
        ApiOperation.EXTERNAL_SEARCH -> "Podaj nazwę potrawy o długości od 1 do 100 znaków."
        ApiOperation.ADD_RECIPE, ApiOperation.UPDATE_RECIPE -> "${operation.failureMessage} Sprawdź dane przepisu."
        else -> operation.failureMessage
    }
    status in 403..404 && (operation == ApiOperation.UPDATE_RECIPE || operation == ApiOperation.DELETE_RECIPE) ->
        "${operation.failureMessage} Przepis jest już niedostępny lub nie masz uprawnień."
    status == 502 && operation == ApiOperation.EXTERNAL_SEARCH ->
        "TheMealDB jest chwilowo niedostępne. Spróbuj ponownie później."
    status == 504 && operation == ApiOperation.EXTERNAL_SEARCH ->
        "TheMealDB nie odpowiedziało na czas. Spróbuj ponownie."
    status >= 500 -> "${operation.failureMessage} Serwer CookBook ma problem. Spróbuj ponownie później."
    else -> "${operation.failureMessage} Spróbuj ponownie."
}
