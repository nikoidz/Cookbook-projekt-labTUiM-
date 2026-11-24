# CookBook

CookBook to aplikacja na Androida, w której możesz dodać własny przepis, znaleźć pomysł na obiad i zobaczyć przepisy dodane przez innych. To projekt laboratoryjny. Ten przewodnik pozwala najpierw uruchomić aplikację, a potem zrozumieć, co dzieje się po kliknięciu przycisku.

## Chcę zobaczyć działającą aplikację

1. Na telefonie z Androidem 8.0 lub nowszym otwórz otrzymany plik `CookBook-chmura.apk` i zainstaluj aplikację. Jeśli system wyświetli pytanie o instalację z tego źródła, zezwól dla aplikacji, z której otwierasz plik. Na emulatorze możesz przeciągnąć APK na jego okno.
2. Przed pokazem otwórz [status backendu](https://cookbook-backend-vovr.onrender.com/health) i poczekaj na `{"status":"ok"}`. Darmowa usługa Render po 15 minutach bez ruchu zasypia. Pierwsze uruchomienie może potrwać około minuty. [Dokumentacja Rendera](https://render.com/docs/free#spinning-down-on-idle)
3. Uruchom **CookBook (chmura)**. Kliknij **Nie masz konta? Zarejestruj się**, załóż własne konto i zaloguj się jego adresem e-mail. To konto aplikacji CookBook.
4. Wybierz **Dodaj przepis**. Uzupełnij nazwę, czas, składniki i kroki. Zapisany wpis zobaczysz w **Moich przepisach** oraz na [publicznej liście przepisów](https://cookbook-backend-vovr.onrender.com/recipes), którą można też otworzyć w przeglądarce.

Plik APK jest podpisaną wersją demonstracyjną `cloudDebug`. Łączy się z istniejącym backendem na Renderze. Jeśli przekazujesz projekt prowadzącemu, daj mu link do repozytorium i plik APK. Na telefonie wystarczy połączenie z internetem, a z repozytorium można zbudować własne APK lub uruchomić cały projekt lokalnie.

## Co z czym się komunikuje

Po wejściu w **Wszystkie przepisy** Android wysyła żądanie `GET /recipes` do backendu. Backend pyta bazę o publiczne przepisy i odsyła wynik jako JSON. Aplikacja zamienia go na listę kart. Przy zapisie aplikacja wysyła formularz wraz z tokenem sesji. Backend sprawdza, kto jest zalogowany, i przypisuje przepis do tego konta.

| Element | Za co odpowiada |
| --- | --- |
| **Android i Jetpack Compose** | Ekrany aplikacji. Android wysyła żądania HTTP przy użyciu OkHttp. |
| **Ktor na Renderze** | Odbiera żądania HTTPS, sprawdza dane i uprawnienia oraz odczytuje i zapisuje dane przez JDBC. Render uruchamia obraz Docker z backendem. |
| **PostgreSQL na Neon** | Przechowuje konta, przepisy i sesje. Dane są w bazie `neondb`, w gałęzi `production`. |
| **TheMealDB** | Zewnętrzny katalog. Backend pyta go po kliknięciu **Przepisy z internetu** i przekazuje wynik aplikacji. Samo wyszukiwanie niczego nie dopisuje do bazy CookBooka. |
| **GitHub i GitHub Desktop** | Repozytorium zawiera kod obu modułów. GitHub Desktop wysyła zmiany do GitHuba. Render pobiera z niego kod backendu przy wdrożeniu. |
| **Android Studio, emulator, Neon Console i pgAdmin** | Studio buduje aplikację, emulator działa jak wirtualny telefon. Neon Console obsługuje bazę w chmurze, a pgAdmin pokazuje ten PostgreSQL, z którym go połączysz. |

**`localDebug`** łączy się z backendem i bazą na Twoim komputerze. **`cloudDebug`** i **`cloudRelease`** łączą się z publicznym adresem HTTPS ustawionym podczas kompilacji. Wersja chmurowa instaluje się obok lokalnej. Dane i konta z obu środowisk są odrębne.

## Co jest w repozytorium

| Folder | Zawartość |
| --- | --- |
| [`cookbookbackend`](cookbookbackend) | Serwer Ktor 3.6.0, Dockerfile, skrypty PostgreSQL, testy backendu i [dodatkowe instrukcje wdrożeniowe](cookbookbackend/README.md). |
| [`lab - cookbook - mobilka`](lab%20-%20cookbook%20-%20mobilka) | Aplikacja Kotlin i Compose, konfiguracja Gradle, testy jednostkowe i testy emulatora. |

Oba projekty mają własne pliki `gradlew.bat` i `gradlew`. Osoba pobierająca repozytorium dostaje źródła i skrypty budowania. SDK Androida, lokalny PostgreSQL i hasła konfiguruje na swoim komputerze.

## Uruchomienie ze źródeł na Windows

### Sama aplikacja chmurowa

Zainstaluj Android Studio z obsługą Android Gradle Plugin 9.4.1. W **SDK Manager** doinstaluj platformę **Android API 37** i **Android SDK Platform-Tools**. W **Device Manager** możesz utworzyć emulator z obrazem Androida obsługującym aplikację. Zainstaluj JDK 25 i skopiuj ścieżkę do jego katalogu. Gradle Wrapper pobiera Gradle i zależności przy pierwszym uruchomieniu.

Z katalogu głównego repozytorium uruchom w PowerShellu:

```powershell
$env:JAVA_HOME = Read-Host 'Pełna ścieżka do katalogu JDK 25'
$env:ANDROID_HOME = Join-Path $env:LOCALAPPDATA 'Android\Sdk'
Set-Location '.\lab - cookbook - mobilka'
.\gradlew.bat '-PCOOKBOOK_API_URL=https://cookbook-backend-vovr.onrender.com' :app:assembleCloudDebug
```

Jeśli Android Studio pokazuje inny **Android SDK Location**, wpisz tę ścieżkę jako `ANDROID_HOME`.

Gotowy plik jest w `app/build/outputs/apk/cloud/debug/app-cloud-debug.apk`. Jeśli chcesz uruchamiać chmurowy wariant bezpośrednio z Android Studio, otwórz folder aplikacji, dodaj poniższą właściwość do jego `gradle.properties`, zsynchronizuj projekt i wybierz `cloudDebug` w **Build Variants**.

```properties
COOKBOOK_API_URL=https://cookbook-backend-vovr.onrender.com
```

Adres możesz zmienić na własne wdrożenie. Podaj sam adres HTTPS, bez dopisywania `/recipes`. `cloudRelease` służy do przygotowania wydania podpisywanego własnym kluczem.

### Backend, baza i Android lokalnie

Potrzebujesz **JDK 21** dla backendu, **PostgreSQL 18** z klientem `psql` oraz Android Studio z SDK i emulatorem jak wyżej. Uruchom lokalną usługę PostgreSQL. Jeśli PowerShell nie widzi polecenia `psql`, w poniższych komendach użyj np. `& 'C:\Program Files\PostgreSQL\18\bin\psql.exe'` w jego miejsce. Poniższa inicjalizacja dotyczy nowej, pustej bazy i nowej roli aplikacji. Jeśli masz już działającą lokalną konfigurację, użyj jej danych połączenia.

Z katalogu `cookbookbackend` wykonaj:

```powershell
psql -h localhost -p 5432 -U postgres -d postgres -c 'CREATE DATABASE cookbook_db;'
psql -h localhost -p 5432 -U postgres -d cookbook_db -v ON_ERROR_STOP=1 -f db/schema.sql -f db/runtime-role.sql
psql -h localhost -p 5432 -U postgres -d cookbook_db -c '\password cookbook_app'
```

`psql` poprosi o hasło lokalnego administratora `postgres`. Ostatnia komenda poprosi o ustawienie hasła dla roli `cookbook_app`, której użyje backend. W tej samej sesji PowerShella ustaw zmienne i uruchom serwer:

```powershell
$env:JAVA_HOME = Read-Host 'Pełna ścieżka do katalogu JDK 21'
$env:COOKBOOK_DB_URL = 'jdbc:postgresql://localhost:5432/cookbook_db'
$env:COOKBOOK_DB_USER = 'cookbook_app'
$cookbookPassword = Read-Host 'Hasło lokalnej roli cookbook_app' -AsSecureString
$cookbookCredential = [pscredential]::new('cookbook_app', $cookbookPassword)
$env:COOKBOOK_DB_PASSWORD = $cookbookCredential.GetNetworkCredential().Password
try {
    .\gradlew.bat run
} finally {
    Remove-Item Env:COOKBOOK_DB_PASSWORD
}
```

Otwórz [lokalne `/health`](http://127.0.0.1:8080/health) i [lokalne `/recipes`](http://127.0.0.1:8080/recipes). `/health` sprawdza proces, a `/recipes` sprawdza też połączenie z bazą. Jeśli baza jest pusta, `/recipes` zwróci `[]`. Zostaw backend włączony.

W Android Studio otwórz folder `lab - cookbook - mobilka`, wybierz **Build Variants → localDebug** i uruchom aplikację na emulatorze. Jeśli aplikacja poprosi o dostęp do sieci lokalnej, zezwól. Emulator używa `10.0.2.2:8080`, pod którym widzi port 8080 Twojego komputera. Na telefonie fizycznym do pokazu wybierz wariant chmurowy. [Adresy sieciowe emulatora](https://developer.android.com/studio/run/emulator-networking-address)

## Baza danych

| Tabela | Najważniejsze pola |
| --- | --- |
| `users` | `id` identyfikuje konto. `username` i `email` są unikatowe. `password_hash` przechowuje skrót hasła. `created_at` to data założenia konta. |
| `recipes` | `id`, `title`, `preparation_time`, tablice `ingredients` i `steps`, flaga `is_public` i data `created_at`. `author_id` wskazuje na `users.id`. |
| `user_sessions` | `token_hash` jest identyfikatorem zapisanej sesji. `user_id` wskazuje na konto. `created_at` i `expires_at` określają czas utworzenia i wygaśnięcia. |

Jedno konto może mieć wiele przepisów i sesji. Nowy przepis z aplikacji jest publiczny. Użytkownik może edytować i usuwać przepisy przypisane do jego konta. Hasła są haszowane z użyciem PBKDF2 i indywidualnej soli, a backend przechowuje skrót tokenu sesji. Nowa sesja ma ważność 7 dni. Wylogowanie unieważnia jej token. Strukturę bazy tworzy [`db/schema.sql`](cookbookbackend/db/schema.sql), a uprawnienia roli aplikacji nadaje [`db/runtime-role.sql`](cookbookbackend/db/runtime-role.sql).

Aby zobaczyć przepisy w chmurze, w Neon Console wybierz projekt `cookbook`, **Postgres database → SQL Editor**, gałąź `production` i bazę `neondb`. Wklej zapytanie i kliknij **Run**. [Instrukcja edytora SQL](https://neon.com/docs/get-started/query-with-neon-sql-editor)

```sql
SELECT id, title, author_id, preparation_time, is_public
FROM public.recipes
ORDER BY id DESC;
```

## API w jednym miejscu

Żądania z danymi używają JSON. W operacjach oznaczonych jako wymagające sesji klient wysyła nagłówek `Authorization: Bearer <token>`. Po zalogowaniu aplikacja zapisuje token w zaszyfrowanym schowku opartym na Android Keystore.

| Metoda i ścieżka | Sesja | Dane wejściowe i wynik |
| --- | --- | --- |
| `GET /` | — | Tekst potwierdzający uruchomienie, `200`. |
| `GET /health` | — | `{"status":"ok"}`, `200`. |
| `POST /auth/register` | — | `username`, `email`, `password`. Zwraca konto z `id`, `username`, `email`, `201`. |
| `POST /auth/login` | — | `email`, `password`. Zwraca `token`, `userId`, `username`, `200`. |
| `GET /auth/me` | Tak | Dane aktualnego konta, `200`. |
| `POST /auth/logout` | Tak | Unieważnia podany token, `204`. |
| `GET /recipes` | — | Tablica publicznych przepisów, `200`. |
| `GET /recipes/mine` | Tak | Tablica przepisów zalogowanego użytkownika, `200`. |
| `GET /recipes/{id}` | — | Szczegóły publicznego przepisu, `200`. |
| `POST /recipes` | Tak | Zapisuje nowy przepis i zwraca go z `id`, `201`. |
| `PUT /recipes/{id}` | Tak | Zapisuje zmiany we własnym przepisie i zwraca jego dane, `200`. |
| `DELETE /recipes/{id}` | Tak | Usuwa własny przepis i zwraca potwierdzenie, `200`. |
| `GET /external/recipes?search=chicken` | — | Wyszukuje po nazwie. Wynik zawiera `id`, `title`, `ingredients`, `instructions` i `imageUrl`, `200`. |

Przykładowa treść `POST /recipes` lub `PUT /recipes/{id}`:

```json
{
  "title": "Zupa pomidorowa",
  "preparationTime": "25 min",
  "ingredients": ["Pomidory", "Bulion"],
  "steps": ["Pokrój pomidory", "Gotuj składniki w bulionie"]
}
```

Najczęstsze błędy to `400` dla niepoprawnych danych, `401` dla błędnego logowania lub nieważnej sesji, `404` dla niedostępnego przepisu, `409` dla zajętego e-maila lub nazwy i `429` po przekroczeniu limitu logowania lub rejestracji. Przy problemach po stronie TheMealDB backend zwraca `502`, a przy przekroczeniu czasu oczekiwania `504`. Błędy API mają pole `error` z komunikatem.

## Wdrożenie i aktualizacja

Render buduje gałąź `main` z katalogu `cookbookbackend`. Usługa używa środowiska **Docker** z plikiem `Dockerfile`, regionu **Frankfurt**, planu **Free** i kontroli `/health`. Publiczny adres używa HTTPS. Render nadaje numer portu w `PORT`.

Backend dostaje adres `COOKBOOK_DB_URL` w formacie JDBC, użytkownika `COOKBOOK_DB_USER=cookbook_app`, hasło w `COOKBOOK_DB_PASSWORD` i ustawienie `COOKBOOK_INGRESS=render`. URL bazy zawiera `sslmode=verify-full&sslfactory=org.postgresql.ssl.DefaultJavaSSLFactory`. Kontener ma domyślnie `COOKBOOK_HOST=0.0.0.0`. Wartość hasła ustawiasz w **Environment** na Renderze.

Zmiany backendu wgrane do `main` można wdrożyć z panelu **Deploys → Manual Deploy → Deploy latest commit**. Po wdrożeniu sprawdź `/health`, `/recipes` i wyszukiwanie w aplikacji. Po zmianach w kodzie Androida zbuduj i przekaż nowy APK. Aktualizacja pod tym samym identyfikatorem w Androidzie wymaga podpisania nowej wersji tym samym kluczem.

## Testy przed oddaniem

| Sprawdzenie | Oczekiwany wynik |
| --- | --- |
| Backend z jednorazowym PostgreSQL | Rejestracja i logowanie działają, duplikaty i złe hasło są odrzucane, a wygasły lub unieważniony token nie daje dostępu. Operacje na własnym przepisie działają, obce konto widzi wpis publiczny bez możliwości edycji i usunięcia. Nowa instancja backendu nadal widzi przepis i sesję. |
| Android lokalny i chmurowy | Kompilacja działa, walidacja formularzy i zapis sesji przechodzą testy. Scenariusz na emulatorze pokrywa rejestrację, logowanie, dodanie, wyszukanie, szczegóły, edycję, ponowne otwarcie aplikacji, usunięcie i wylogowanie. |
| Sieć i zewnętrzne API | Przy niedostępnym backendzie lub sieci użytkownik widzi odpowiedni komunikat. TheMealDB zwraca wyniki online, a testy backendu symulują awarię i opóźnienie dostawcy. |
| Wdrożenie | `/health` i `/recipes` zwracają HTTP 200. Przepis dodany z **CookBook (chmura)** pojawia się na publicznej liście i w Neon. |

### Wyniki weryfikacji projektu

- Z czystej kopii repozytorium zbudowano backend, obraz Docker oraz warianty `localDebug`, `cloudDebug` i `cloudRelease`. Lokalny kontener odpowiedział na `/health`. Do kopii dodano aktualne pliki testów etapu F, żeby zweryfikować również przypadki przygotowane później niż pierwotne wdrożenie.
- Przeszło **16 testów backendu** na wydzielonym PostgreSQL, **10 testów jednostkowych Androida** i **12 podstawowych testów na emulatorze**. Na emulatorze przeszedł także scenariusz z rejestracją, operacjami na przepisach, wyszukiwaniem TheMealDB i ponownym otwarciem aplikacji. Sprawdzono osobno komunikaty przy niedostępnym backendzie i przy wyłączonej sieci.
- W chmurze wszystkie trzy odczyty zwróciły `200`. `/health` zwrócił `ok`, `/recipes` zawierało dodany przepis, a wyszukiwanie `chicken` zwróciło 25 wyników. Zakres sprawdzenia chmury obejmował odczyty. Operacje testowe, w tym kasowanie, wykonano na osobnej bazie.
- Zweryfikowano podpis pliku `CookBook-chmura.apk`, zainstalowano go na emulatorze i uruchomiono aplikację. Plik jest przeznaczony do instalacji demonstracyjnej na Androidzie od API 26.

W `cookbookbackend` komenda `./gradlew test` (na Windows `.\gradlew.bat test`) uruchamia testy. Testy PostgreSQL wymagają osobnej, jednorazowej bazy ze schematem i rolą aplikacji. Podaj jej dane przez `COOKBOOK_TEST_DB_URL`, `COOKBOOK_TEST_DB_USER` i `COOKBOOK_TEST_DB_PASSWORD`. Do testów jednostkowych aplikacji użyj `:app:testLocalDebugUnitTest` i `:app:testCloudDebugUnitTest`. Dla chmurowych zadań podaj `-PCOOKBOOK_API_URL=...` tak jak przy kompilacji.

Aby uruchomić podstawowe testy ekranów i sesji, włącz emulator i użyj poniższego polecenia z katalogu aplikacji. Parametr `notClass` wyklucza testy wymagające specjalnego przygotowania bazy lub sieci. Użyj świeżego emulatora lub aplikacji testowej z czystą sesją, ponieważ testy formularzy zaczynają się na ekranie logowania. [Opcje AndroidJUnitRunner](https://developer.android.com/reference/androidx/test/runner/AndroidJUnitRunner)

```powershell
.\gradlew.bat '-PCOOKBOOK_API_URL=https://cookbook-backend-vovr.onrender.com' `
    '-Pandroid.testInstrumentationRunnerArguments.notClass=com.example.cookbook.RecipeFlowTest' `
    :app:connectedLocalDebugAndroidTest :app:connectedCloudDebugAndroidTest
```

`RecipeFlowTest` jest uruchamiany świadomie na świeżej instalacji wariantu `localDebug` z **jednorazowym backendem i bazą**. Przekaż `-Pandroid.testInstrumentationRunnerArguments.cookbookDisposableBackend=true`. Do wyboru pojedynczego scenariusza służy argument `class` z klasą i nazwą metody, np. `com.example.cookbook.RecipeFlowTest#accountCanSaveFindEditAndRemoveARecipe`. Scenariusz `loginReportsTheConnectionProblem` dodatkowo wymaga `cookbookNetworkState=backendStopped` albo `cookbookNetworkState=offline` w argumentach instrumentacji oraz odpowiednio przygotowanego emulatora.

## Wnioski

Udało się połączyć aplikację mobilną z własnym API, trwałą bazą danych i zewnętrzną wyszukiwarką. Osobne warianty Androida rozwiązały problem przełączania adresów serwera i pozwoliły zachować dotychczasowe dane lokalne. Użycie Neona pozwala zachować dane przy restarcie lub uśpieniu usługi w Renderze. W przyszłości można dodać odzyskiwanie hasła i osobny tryb szkiców prywatnych.

Jeśli logowanie kiedyś działało lokalnie, a chmurowa aplikacja odrzuca te same dane, sprawdź, czy konto było zakładane w chmurze. Jeśli aplikacja zgłasza problem z serwerem, otwórz jej `/health`, a po odpowiedzi `ok` sprawdź `/recipes`. To pozwala ustalić, czy problem dotyczy samego serwera, połączenia z bazą czy logowania do konkretnego konta.
