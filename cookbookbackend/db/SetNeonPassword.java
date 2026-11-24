import java.io.Console;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import org.postgresql.core.Utils;

class SetNeonPassword {
    private static final Gson gson = new Gson();

    public static void main(String[] args) {
        Console console = System.console();
        if (console == null) {
            System.err.println("Uruchom polecenie we wlasnym oknie PowerShell, bez przekierowania wejscia.");
            System.exit(1);
            return;
        }

        String input = args.length == 1 ? args[0] : console.readLine("Wklej sam host Direct z Neon: ");
        String host = input == null ? "" : input.trim().toLowerCase(Locale.ROOT).replaceFirst("\\.$", "");
        if (!host.matches("ep-[a-z0-9-]+(\\.[a-z0-9][a-z0-9-]*)+\\.neon\\.tech")
                || host.matches("[^.]+-pooler\\..+")) {
            System.err.println("Podaj bezposredni host Neon konczacy sie na .neon.tech, bez -pooler.");
            System.exit(1);
            return;
        }

        char[] ownerPassword = console.readPassword("Haslo neondb_owner z panelu Neon: ");
        char[] appPassword = null;
        char[] confirmation = null;
        boolean changed = false;
        String stage = "sprawdzenie hasla neondb_owner";
        int exitCode = 0;
        try (HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NEVER).build()) {
            if (ownerPassword == null || ownerPassword.length == 0) {
                System.err.println("Wpisz haslo neondb_owner z panelu Neon.");
                exitCode = 1;
            } else {
                verifyRole(client, host, "neondb_owner", ownerPassword, "SELECT current_user AS role");
                System.out.println("Host i haslo neondb_owner sa poprawne.");

                appPassword = console.readPassword("Nowe haslo cookbook_app (co najmniej 16 znakow): ");
                confirmation = console.readPassword("Powtorz nowe haslo cookbook_app: ");
                if (appPassword == null || appPassword.length < 16
                    || !Arrays.equals(appPassword, confirmation) || Arrays.equals(ownerPassword, appPassword)) {
                    System.err.println("Uzyj osobnego hasla co najmniej 16 znakow i wpisz je identycznie dwa razy.");
                    exitCode = 1;
                } else {
                    stage = "ustawianie hasla cookbook_app";
                    StringBuilder sql = new StringBuilder("ALTER ROLE cookbook_app WITH PASSWORD ");
                    boolean escapeBackslashes = new String(appPassword).contains("\\");
                    sql.append(escapeBackslashes ? "E'" : "'");
                    Utils.escapeLiteral(sql, new String(appPassword), !escapeBackslashes);
                    sql.append('\'');
                    query(client, host, "neondb_owner", ownerPassword, sql.toString());
                    changed = true;

                    stage = "sprawdzenie polaczenia cookbook_app";
                    verifyRole(client, host, "cookbook_app", appPassword,
                            "SELECT current_user AS role, count(*) AS recipes FROM public.recipes");
                    System.out.println("Haslo ustawione. Polaczenie jako cookbook_app dziala.");
                }
            }
        } catch (SQLException error) {
            System.err.println("Etap: " + stage + ".");
            System.err.println(changed ? "Haslo zostalo ustawione, ale test polaczenia nie przeszedl."
                    : "Neon nie potwierdzil ustawienia hasla.");
            System.err.println("Kod PostgreSQL: " + error.getSQLState());
            exitCode = 1;
        } catch (IOException | InterruptedException error) {
            if (error instanceof InterruptedException) Thread.currentThread().interrupt();
            System.err.println("Etap: " + stage + ".");
            System.err.println(changed ? "Haslo zostalo ustawione, ale test polaczenia nie przeszedl."
                    : "Neon nie potwierdzil ustawienia hasla.");
            System.err.println("Blad HTTPS: " + error.getClass().getSimpleName());
            exitCode = 1;
        } finally {
            if (ownerPassword != null) Arrays.fill(ownerPassword, '\0');
            if (appPassword != null) Arrays.fill(appPassword, '\0');
            if (confirmation != null) Arrays.fill(confirmation, '\0');
        }
        if (exitCode != 0) System.exit(exitCode);
    }

    private static void verifyRole(HttpClient client, String host, String user, char[] password, String sql)
            throws IOException, InterruptedException, SQLException {
        var rows = query(client, host, user, password, sql).getAsJsonArray("rows");
        if (rows == null || rows.size() != 1 || !user.equals(rows.get(0).getAsJsonArray().get(0).getAsString())) {
            throw new SQLException("Unexpected database role", "28000");
        }
    }

    private static JsonObject query(HttpClient client, String host, String user, char[] password, String sql)
            throws IOException, InterruptedException, SQLException {
        String encoded = URLEncoder.encode(new String(password), StandardCharsets.UTF_8).replace("+", "%20");
        String connectionString = "postgresql://" + user + ":" + encoded + "@" + host
                + "/neondb?sslmode=require&channel_binding=require";
        String regionHost = host.substring(host.indexOf('.') + 1);
        HttpRequest request = HttpRequest.newBuilder(URI.create("https://api." + regionHost + "/sql"))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json")
                .header("Neon-Connection-String", connectionString)
                .header("Neon-Array-Mode", "true")
                .header("Neon-Raw-Text-Output", "true")
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(Map.of("query", sql, "params", List.of()))))
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        JsonObject body;
        try {
            body = gson.fromJson(response.body(), JsonObject.class);
        } catch (JsonParseException invalid) {
            body = null;
        }
        if (response.statusCode() != 200 || body == null) {
            String code = body != null && body.has("code") && body.get("code").isJsonPrimitive()
                    ? body.get("code").getAsString() : "";
            String message = body != null && body.has("message") && body.get("message").isJsonPrimitive()
                    ? body.get("message").getAsString().toLowerCase(Locale.ROOT) : "";
            if (message.contains("password authentication failed")) {
                System.err.println("Neon odrzucil haslo uzytkownika " + user + ".");
            } else if (message.contains("entropy") || message.contains("password is too weak")) {
                System.err.println("Neon uznal nowe haslo za zbyt slabe.");
            } else if (message.contains("permission denied") || message.contains("insufficient privilege")) {
                System.err.println("Neon odrzucil operacje z powodu uprawnien.");
            } else if (message.contains("couldn't connect to compute") || message.contains("failed to connect to compute")) {
                System.err.println("Neon nie mogl polaczyc sie z maszyna bazy danych.");
            } else {
                System.err.println("Odpowiedz Neon HTTPS: " + response.statusCode() + ".");
            }
            throw new SQLException("Neon HTTPS request failed", code.matches("[A-Z0-9]{5}") ? code : "brak");
        }
        return body;
    }
}
