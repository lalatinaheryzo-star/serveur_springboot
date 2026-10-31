import com.sun.net.httpserver.HttpServer;
import java.awt.Desktop;
import java.io.Console;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GetRefreshToken {

    static final String AUTH_URL =
            "https://accounts.google.com/o/oauth2/v2/auth";

    static final String TOKEN_URL =
            "https://oauth2.googleapis.com/token";

    static final String SCOPE =
            "https://www.googleapis.com/auth/gmail.send";

    static final SecureRandom RNG = new SecureRandom();

    static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public static void main(String[] args) {

        HttpServer serveur = null;

        try {
            Console console = System.console();

            if (console == null) {
                throw new IllegalStateException(
                        "Console non disponible. Ouvrez Windows PowerShell classique, "
                        + "pas le terminal sans saisie masquee."
                );
            }

            System.out.println("=== MANAO - Gmail API OAuth ===");
            System.out.println("Outil temporaire : aucun token n'est ecrit sur disque.");
            System.out.println();

            String clientId = console.readLine(
                    "ID client OAuth (Application de bureau) : "
            ).trim();

            char[] secretChars = console.readPassword(
                    "Secret client (saisie masquee) : "
            );

            if (secretChars == null || secretChars.length == 0) {
                throw new IllegalStateException("Client Secret vide.");
            }

            String clientSecret = new String(secretChars);
            java.util.Arrays.fill(secretChars, '\0');

            String loginHint = console.readLine(
                    "Adresse Gmail expeditrice (facultatif) : "
            );

            if (loginHint == null) loginHint = "";
            loginHint = loginHint.trim();

            if (clientId.isEmpty()) {
                throw new IllegalStateException("ID client vide.");
            }

            if (!clientId.endsWith(".apps.googleusercontent.com")) {
                throw new IllegalStateException(
                        "Format de Client ID OAuth inattendu."
                );
            }

            // PKCE S256 et protection anti-CSRF
            String verifier = aleatoireUrl(64);

            String challenge = b64url(
                    sha256(verifier.getBytes(StandardCharsets.US_ASCII))
            );

            String state = aleatoireUrl(24);

            CompletableFuture<Map<String, String>> retour =
                    new CompletableFuture<>();

            serveur = HttpServer.create(
                    new InetSocketAddress(
                            InetAddress.getByName("127.0.0.1"), 0
                    ),
                    0
            );

            final String expectedState = state;

            serveur.createContext("/", ex -> {

                try {
                    // Accepter uniquement GET depuis la machine locale.
                    if (!"GET".equalsIgnoreCase(ex.getRequestMethod())
                            || !ex.getRemoteAddress().getAddress().isLoopbackAddress()) {

                        ex.sendResponseHeaders(403, -1);
                        ex.close();
                        return;
                    }

                    Map<String, String> params =
                            parseRequete(ex.getRequestURI().getRawQuery());

                    // Vérifier state AVANT d'accepter le callback.
                    if (!expectedState.equals(params.get("state"))) {
                        byte[] msg = "Reponse OAuth invalide.".getBytes(
                                StandardCharsets.UTF_8
                        );

                        ex.sendResponseHeaders(400, msg.length);
                        ex.getResponseBody().write(msg);
                        ex.close();
                        return;
                    }

                    if (!params.containsKey("code")
                            && !params.containsKey("error")) {

                        ex.sendResponseHeaders(400, -1);
                        ex.close();
                        return;
                    }

                    byte[] corps = (
                            "<html><head><meta charset=\"utf-8\">"
                            + "<title>MANAO</title></head><body>"
                            + "<h3>Reponse recue.</h3>"
                            + "<p>Vous pouvez fermer cet onglet et revenir au terminal.</p>"
                            + "</body></html>"
                    ).getBytes(StandardCharsets.UTF_8);

                    ex.getResponseHeaders().add(
                            "Content-Type", "text/html; charset=utf-8"
                    );

                    ex.sendResponseHeaders(200, corps.length);
                    ex.getResponseBody().write(corps);
                    ex.close();

                    retour.complete(params);

                } catch (Exception e) {
                    try {
                        ex.close();
                    } catch (Exception ignored) {
                    }
                }
            });

            serveur.start();

            int port = serveur.getAddress().getPort();

            String redirectUri = "http://127.0.0.1:" + port + "/";

            String url = AUTH_URL
                    + "?client_id=" + enc(clientId)
                    + "&redirect_uri=" + enc(redirectUri)
                    + "&response_type=code"
                    + "&scope=" + enc(SCOPE)
                    + "&access_type=offline"
                    + "&prompt=consent"
                    + "&code_challenge=" + enc(challenge)
                    + "&code_challenge_method=S256"
                    + "&state=" + enc(state)
                    + (loginHint.isEmpty()
                    ? ""
                    : "&login_hint=" + enc(loginHint));

            System.out.println();
            System.out.println("Ouverture du navigateur...");

            try {
                if (Desktop.isDesktopSupported()
                        && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {

                    Desktop.getDesktop().browse(URI.create(url));

                } else {
                    System.out.println(
                            "Copiez ce lien dans votre navigateur :"
                    );
                    System.out.println(url);
                }
            } catch (Exception e) {
                System.out.println(
                        "Ouverture automatique impossible. Copiez ce lien :"
                );
                System.out.println(url);
            }

            System.out.println();
            System.out.println("En attente de l'autorisation Google (5 minutes)...");

            Map<String, String> params =
                    retour.get(5, TimeUnit.MINUTES);

            if (params.containsKey("error")) {
                throw new IllegalStateException(
                        "Autorisation refusee ou annulee par Google : "
                        + params.get("error")
                );
            }

            String code = params.get("code");

            if (code == null || code.isBlank()) {
                throw new IllegalStateException(
                        "Code d'autorisation absent."
                );
            }

            // Echange du code contre les tokens.
            String reponse = poster(
                    "code=" + enc(code)
                    + "&client_id=" + enc(clientId)
                    + "&client_secret=" + enc(clientSecret)
                    + "&redirect_uri=" + enc(redirectUri)
                    + "&grant_type=authorization_code"
                    + "&code_verifier=" + enc(verifier)
            );

            String refreshToken = champ(reponse, "refresh_token");
            String scopeAccorde = champ(reponse, "scope");

            if (refreshToken == null || refreshToken.isBlank()) {
                throw new IllegalStateException(
                        "Google n'a pas fourni de refresh token. "
                        + "Retirez l'acces de cette application dans "
                        + "https://myaccount.google.com/permissions "
                        + "puis relancez le programme."
                );
            }

            if (scopeAccorde == null
                    || !scopeAccorde.contains("gmail.send")) {

                throw new IllegalStateException(
                        "Le scope gmail.send n'a pas ete confirme."
                );
            }

            // Verifier le fonctionnement du refresh token.
            String verification = poster(
                    "client_id=" + enc(clientId)
                    + "&client_secret=" + enc(clientSecret)
                    + "&refresh_token=" + enc(refreshToken)
                    + "&grant_type=refresh_token"
            );

            if (champ(verification, "access_token") == null) {
                throw new IllegalStateException(
                        "Le refresh token n'a pas permis d'obtenir "
                        + "un access token."
                );
            }

            System.out.println();
            System.out.println("SUCCES :");
            System.out.println("- Autorisation Google recue.");
            System.out.println("- Scope gmail.send confirme.");
            System.out.println("- Refresh token teste avec succes.");
            System.out.println();
            System.out.println(
                    "IMPORTANT : verifiez dans Google Cloud Console que "
                    + "l'application OAuth est en statut Production."
            );
            System.out.println(
                    "Une application externe en statut Testing peut avoir "
                    + "des refresh tokens limites a 7 jours."
            );
            System.out.println();
            System.out.println(
                    "Copiez le token directement dans Render > Environment."
            );
            System.out.println("Nom : GMAIL_OAUTH_REFRESH_TOKEN");
            System.out.println("-----DEBUT-----");
            System.out.println(refreshToken);
            System.out.println("-----FIN-----");
            System.out.println();
            System.out.println(
                    "Ne partagez jamais ce token. Effacez ensuite l'ecran "
                    + "avec Clear-Host."
            );

        } catch (Exception e) {

            System.out.println();
            System.out.println("ERREUR : " + e.getMessage());

        } finally {

            if (serveur != null) {
                serveur.stop(0);
            }
        }
    }

    // ---------- Utilitaires ----------

    static String poster(String formulaire) throws Exception {

        HttpRequest req = HttpRequest.newBuilder(URI.create(TOKEN_URL))
                .timeout(Duration.ofSeconds(20))
                .header(
                        "Content-Type",
                        "application/x-www-form-urlencoded"
                )
                .POST(HttpRequest.BodyPublishers.ofString(formulaire))
                .build();

        HttpResponse<String> r = HTTP.send(
                req,
                HttpResponse.BodyHandlers.ofString()
        );

        if (r.statusCode() != 200) {

            String erreur = champ(r.body(), "error");
            String hint = "";

            if ("invalid_client".equals(erreur)) {
                hint = " (ID client ou secret incorrect)";
            }

            if ("invalid_grant".equals(erreur)) {
                hint = " (code expire, deja utilise ou autorisation invalide)";
            }

            throw new IllegalStateException(
                    "Echec Google : HTTP " + r.statusCode()
                    + (erreur == null ? "" : ", erreur " + erreur)
                    + hint
            );
        }

        return r.body();
    }

    static String champ(String json, String nom) {

        Matcher m = Pattern.compile(
                "\"" + Pattern.quote(nom) + "\"\\s*:\\s*\"([^\"]*)\""
        ).matcher(json);

        return m.find() ? m.group(1) : null;
    }

    static Map<String, String> parseRequete(String q) {

        Map<String, String> map = new HashMap<>();

        if (q == null) return map;

        for (String kv : q.split("&")) {

            int i = kv.indexOf('=');

            String k = i < 0 ? kv : kv.substring(0, i);
            String v = i < 0 ? "" : kv.substring(i + 1);

            map.put(
                    URLDecoder.decode(k, StandardCharsets.UTF_8),
                    URLDecoder.decode(v, StandardCharsets.UTF_8)
            );
        }

        return map;
    }

    static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    static byte[] sha256(byte[] b) throws Exception {
        return MessageDigest.getInstance("SHA-256").digest(b);
    }

    static String b64url(byte[] b) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }

    static String aleatoireUrl(int octets) {

        byte[] b = new byte[octets];
        RNG.nextBytes(b);

        return b64url(b);
    }
}