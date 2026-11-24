# cookbookbackend

This project was created using the [Ktor Project Generator](https://start.ktor.io).

Here are some useful links to get you started:
 * [Ktor Documentation](https://ktor.io/docs/home.html)
 * [Ktor GitHub page](https://github.com/ktorio/ktor)
 * [Ktor Slack chat](https://app.slack.com/client/T09229ZC6/C0A974TJ9). [Request an invite](https://surveys.jetbrains.com/s3/kotlin-slack-sign-up).


## Features
Here's a list of features included in this project:

| Name | Description |
|------|-------------|

## Building & Running
To build or run the project, use one of the following tasks:


| Task | Description |
|------|-------------|
| `./gradlew test`    | Run the tests     |
| `./gradlew build`   | Build the project |
| `./gradlew run`     | Run the server    |

If the server starts successfully, you'll see the following output:
```
2024-12-04 14:32:45.584 [main] INFO  Application - Application started in 0.303 seconds.
2024-12-04 14:32:45.682 [main] INFO  Application - Responding at http://127.0.0.1:8080
```

## Uruchamianie z ograniczoną rolą PostgreSQL

Backend lokalny nasłuchuje na `127.0.0.1:8080`. Baza domyślna to `cookbook_db` na porcie `5432`. Backend odczytuje hasło wyłącznie ze zmiennej `COOKBOOK_DB_PASSWORD`. Adres i użytkownika można ustawić przez `COOKBOOK_DB_URL` oraz `COOKBOOK_DB_USER`.

Po utworzeniu tabel uruchom jednorazowo `db/runtime-role.sql` w bazie `cookbook_db` przez `psql` jako administrator. Ustaw hasło roli poleceniem `\password cookbook_app` w `psql`. Następnie ustaw `COOKBOOK_DB_USER` na `cookbook_app` i podaj hasło tej roli jako `COOKBOOK_DB_PASSWORD` w środowisku backendu. Skrypt przyznaje tylko uprawnienia do tabel i sekwencji potrzebne do pracy aplikacji.

Backend ogranicza logowanie do 10 prób, a rejestrację do 5 prób na minutę na adres połączenia. Po przekroczeniu zwraca `429` z `Retry-After`. Liczniki są lokalne dla instancji. Przy wdrożeniu publicznym zakończ TLS i zastosuj limit dla adresów klientów na zaufanym proxy. Lokalny ruch emulatora korzysta z adresu `10.0.2.2`.

Test integracyjny wymaga osobnej, jednorazowej bazy z `db/schema.sql` i rolą z `db/runtime-role.sql`. Ustaw `COOKBOOK_TEST_DB_URL`, `COOKBOOK_TEST_DB_USER` i `COOKBOOK_TEST_DB_PASSWORD`, a następnie uruchom `./gradlew test --rerun-tasks`. Test używa wyłącznie konfiguracji testowej i tworzy własne konta.

## Wdrożenie demonstracyjne za 0 zł

Warunki planów sprawdzone 24 listopada 2026 r.

| Opcja | Warunki darmowego planu |
|---|---|
| **Render Free + Neon Free** | Pierwsza opcja przy wdrożeniu bez karty. Neon oferuje 1 GB danych i 100 CU-godzin na projekt miesięcznie. Backend Render usypia po 15 minutach i może poprosić o weryfikację karty przy tworzeniu usługi. Sprawdź to przed publikacją. [Render Free](https://render.com/docs/free) · [Neon Free](https://neon.com/pricing) |
| **Northflank Sandbox** | Backend i PostgreSQL w tym samym projekcie, bez automatycznego usypiania. Cennik deklaruje Sandbox bez karty, ale formularz PostgreSQL dla tego konta wymaga karty do utworzenia dodatku i deklaruje 0 zł za dodatek. [Cennik Northflank](https://northflank.com/pricing) |
| **Render Free + Render Postgres Free** | Szybka konfiguracja do jednorazowej demonstracji. Darmowa baza PostgreSQL wygasa po 30 dniach. [Render Free](https://render.com/docs/free) |

### Render Free i Neon Free

1. W Neon utwórz projekt `cookbook` na planie **Free**. Użyj pustej bazy demonstracyjnej. Połącz się przez `psql` bezpośrednim połączeniem z konta administracyjnego. Uruchom `db/schema.sql`, potem `db/runtime-role.sql`, i ustaw hasło roli poleceniem `\password cookbook_app`.
2. Po zgodzie na udostępnienie backendu wybierz w Render **New → Web Service**, typ **Docker** i plan **Free**. Ustaw `/health` jako **Health Check Path** i `COOKBOOK_INGRESS=render`. Użyj konfiguracji JDBC z tabeli poniżej z nazwą hosta Neon, a hasło roli przekaż jako sekret `COOKBOOK_DB_PASSWORD`. Render przekazuje `PORT` automatycznie. [Konfiguracja Web Service](https://render.com/docs/web-services)
3. Sprawdź `https://ADRES.onrender.com/health` i `/recipes`, potem wpisz adres origin do `COOKBOOK_API_URL`. Przed pokazem otwórz `/health`, aby wybudzić usługę.

### Northflank Sandbox

1. Utwórz projekt w **Developer Sandbox** na **Northflank Cloud**. Wybierz europejski region, jeśli jest dostępny w Sandbox. Użyj wyłącznie planów mieszczących się w bezpłatnym limicie.
2. Dodaj **PostgreSQL Addon** z włączonym **Deploy with TLS**. Pozostaw bazę dostępną wewnątrz projektu. Z sekcji **Local access** skopiuj polecenie tunelowania CLI i połącz się kontem administratora z pustą bazą demonstracyjną. Uruchom w `psql` pliki `db/schema.sql` i `db/runtime-role.sql` w tej kolejności. Poleceniem `\password cookbook_app` ustaw osobne hasło roli aplikacyjnej. Istniejące konta, sesje i przepisy lokalne mogą pozostać w Twoim PostgreSQL. [PostgreSQL na Northflank](https://northflank.com/docs/v1/application/databases-and-persistence/deploy-databases-on-northflank/deploy-postgresql-on-northflank)
3. Po zgodzie na publikację przekaż dostawcy sam katalog backendu, np. przez prywatne repozytorium Git. Utwórz **Combined Service** z typem buildu **Dockerfile**. Dla repozytorium zawierającego wyłącznie backend ustaw `/Dockerfile` i kontekst `/`. Dla repozytorium zawierającego folder `cookbookbackend` ustaw `/cookbookbackend/Dockerfile` i kontekst `/cookbookbackend`. [Budowa z Dockerfile](https://northflank.com/docs/v1/application/build/build-with-a-dockerfile)
4. Dodaj poniższe zmienne w **Runtime variables**, a hasło zapisz w **Secret Group**. Dla bazy użyj wewnętrznej nazwy hosta pasującej do certyfikatu. W połączeniu JDBC z `verify-full` zaufanie do CA zapewnia magazyn certyfikatów Javy. [TLS w sterowniku PostgreSQL](https://jdbc.postgresql.org/documentation/ssl/)

   | Zmienna | Wartość |
   |---|---|
   | `COOKBOOK_HOST` | `0.0.0.0` wewnątrz kontenera |
   | `PORT` | `8080` na Northflank. Na Render użyj portu podanego przez platformę. |
   | `COOKBOOK_INGRESS` | `northflank` dla usługi dostępnej przez publiczny ingress Northflank. Dla alternatywy za publicznym proxy Render użyj `render`. Przy dostępie bezpośrednim użyj `direct`. |
   | `COOKBOOK_DB_URL` | `jdbc:postgresql://HOST:5432/BAZA?sslmode=verify-full&sslfactory=org.postgresql.ssl.DefaultJavaSSLFactory` |
   | `COOKBOOK_DB_USER` | `cookbook_app` |
   | `COOKBOOK_DB_PASSWORD` | Hasło roli `cookbook_app` |

5. Dodaj jeden publiczny port **HTTP 8080**. Northflank wystawia publiczny adres HTTPS z zarządzanym certyfikatem. Ustaw sprawdzenie **readiness** i **liveness** na `GET /health`, port `8080`. Endpoint nie wymaga połączenia z bazą, więc chwilowa przerwa PostgreSQL nie uruchamia pętli restartów backendu. Dostęp do bazy sprawdź osobno przez `GET /recipes`, który w pustej bazie zwróci `[]`. [Sieć i HTTPS](https://northflank.com/docs/v1/application/network/networking-on-northflank) · [Health checks](https://northflank.com/docs/v1/application/observe/configure-health-checks)

Publiczny ingress ustawia nagłówek identyfikujący klienta na potrzeby limitu logowania. Włącz tryb `northflank` albo `render` wyłącznie wtedy, gdy żądania publiczne przechodzą przez odpowiedni zaufany ingress. Udostępnij publicznie aplikację API, a połączenie PostgreSQL pozostaw prywatne i szyfrowane. [Nagłówki Northflank](https://northflank.com/docs/v1/application/network/networking-on-northflank) · [Nagłówek Render](https://render.com/articles/host-pocketbase-on-render)

### Android i test po wdrożeniu

- `localDebug` korzysta z `http://10.0.2.2:8080` i z istniejącej instalacji CookBook. Wymaga lokalnie uruchomionego backendu.
- Aby włączyć `cloudDebug` i `cloudRelease`, ustaw `COOKBOOK_API_URL` na publiczny adres HTTPS bez ścieżki, np. `https://twoj-adres.code.run`. Możesz ustawić właściwość w `gradle.properties` projektu Android lub podać ją przy kompilacji. Po synchronizacji wybierz wariant w **Build Variants**. CookBook (chmura) instaluje się jako osobna aplikacja i ma własną sesję.

   ```powershell
   .\gradlew.bat "-PCOOKBOOK_API_URL=https://twoj-adres.code.run" assembleCloudDebug
   ```

- Otwórz publiczne `/health` i `/recipes`, a następnie uruchom wariant chmurowy i zarejestruj konto demonstracyjne. Dodaj przepis, uruchom usługę ponownie z panelu i sprawdź, czy przepis wciąż jest dostępny. Publiczny adres kończy się na domenie dostawcy i nie wymaga płatnej domeny.
