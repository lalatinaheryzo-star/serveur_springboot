package mg.manao.backend.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Envoi Gmail API par HTTPS. Les secrets OAuth2 sont fournis uniquement par les propriétés
 * Spring alimentées par l'environnement ; les jetons et le contenu des messages ne sont jamais journalisés.
 */
@Component
public class GmailApiSender {

    private static final Pattern ACCESS_TOKEN = Pattern.compile("\"access_token\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern EXPIRES_IN = Pattern.compile("\"expires_in\"\\s*:\\s*(\\d+)");
    private static final Pattern OAUTH_SCOPE = Pattern.compile("\"scope\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern ERROR = Pattern.compile("\"error\"\\s*:\\s*\"([A-Za-z0-9_.-]+)\"");
    private static final Pattern ERROR_STATUS = Pattern.compile("\"status\"\\s*:\\s*\"([A-Za-z0-9_.-]+)\"");
    private static final Pattern ERROR_REASON = Pattern.compile("\"reason\"\\s*:\\s*\"([A-Za-z0-9_.-]+)\"");
    private static final Pattern MESSAGE_ID = Pattern.compile("\"id\"\\s*:\\s*\"([^\"]+)\"");
    private static final long TOKEN_EXPIRY_MARGIN_SECONDS = 60;

    private final boolean enabled;
    private final String clientId;
    private final String clientSecret;
    private final String refreshToken;
    private final String sender;
    private final String tokenUrl;
    private final String sendUrl;
    private final HttpClient http;

    private final Object tokenLock = new Object();
    private String accessToken;
    private long accessTokenExpiresAtMs;

    public record Resultat(boolean succes, boolean transitoire, String detail) {
        static Resultat ok() {
            return new Resultat(true, false, "message accepté par Gmail");
        }

        static Resultat echec(boolean transitoire, String detail) {
            return new Resultat(false, transitoire, detail);
        }
    }

    public GmailApiSender(
            @Value("${app.gmail-api.enabled:false}") boolean enabled,
            @Value("${app.gmail-api.client-id:}") String clientId,
            @Value("${app.gmail-api.client-secret:}") String clientSecret,
            @Value("${app.gmail-api.refresh-token:}") String refreshToken,
            @Value("${app.gmail-api.sender:}") String sender,
            @Value("${app.gmail-api.token-url:https://oauth2.googleapis.com/token}") String tokenUrl,
            @Value("${app.gmail-api.send-url:https://gmail.googleapis.com/gmail/v1/users/me/messages/send}") String sendUrl) {
        this.enabled = enabled;
        this.clientId = nettoyer(clientId);
        this.clientSecret = nettoyer(clientSecret);
        this.refreshToken = nettoyer(refreshToken);
        this.sender = nettoyer(sender);
        this.tokenUrl = tokenUrl;
        this.sendUrl = sendUrl;
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isConfigured() {
        return enabled && !clientId.isEmpty() && !clientSecret.isEmpty()
                && !refreshToken.isEmpty() && adresseExpediteurValide();
    }

    public String variablesManquantes() {
        StringBuilder missing = new StringBuilder();
        if (clientId.isEmpty()) missing.append("GMAIL_OAUTH_CLIENT_ID ");
        if (clientSecret.isEmpty()) missing.append("GMAIL_OAUTH_CLIENT_SECRET ");
        if (refreshToken.isEmpty()) missing.append("GMAIL_OAUTH_REFRESH_TOKEN ");
        if (!adresseExpediteurValide()) missing.append("GMAIL_SENDER_ADDRESS ");
        return missing.toString().trim();
    }

    /**
     * Retries transient network/429/5xx failures once and refreshes the access token once
     * after a 401. Permanent HTTP errors are returned without retrying.
     */
    public Resultat envoyer(String destinataire, String sujet, String texte) {
        if (!isConfigured()) {
            return Resultat.echec(false,
                    "Gmail API non configurée (variables manquantes : " + variablesManquantes() + ")");
        }

        final String raw;
        try {
            raw = Base64.getUrlEncoder().withoutPadding().encodeToString(
                    construireMime(sender, destinataire, sujet, texte).getBytes(StandardCharsets.UTF_8));
        } catch (IllegalArgumentException e) {
            return Resultat.echec(false, e.getMessage());
        }

        boolean tokenRefreshed = false;
        int transientAttempts = 0;
        while (transientAttempts < 2) {
            try {
                String token = jetonAcces(false);
                HttpResponse<String> response = poster(sendUrl, "{\"raw\":\"" + raw + "\"}",
                        "application/json", token);
                int status = response.statusCode();
                if (status == 200) {
                    Matcher messageId = MESSAGE_ID.matcher(response.body());
                    return messageId.find()
                            ? Resultat.ok()
                            : Resultat.echec(false, "HTTP 200 sans identifiant : envoi non confirmé");
                }
                if (status == 401 && !tokenRefreshed) {
                    tokenRefreshed = true;
                    invaliderJeton();
                    continue;
                }

                boolean transientFailure = status == 429 || status >= 500;
                String detail = detailHttp(status, response.body());
                if (!transientFailure) return Resultat.echec(false, detail);
                transientAttempts++;
                if (transientAttempts >= 2) return Resultat.echec(true, detail);
            } catch (ErreurPermanente e) {
                return Resultat.echec(false, e.getMessage());
            } catch (IOException e) {
                transientAttempts++;
                if (transientAttempts >= 2) {
                    return Resultat.echec(true, "erreur réseau " + e.getClass().getSimpleName());
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return Resultat.echec(true, "envoi interrompu");
            }
            try {
                Thread.sleep(800);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return Resultat.echec(true, "envoi interrompu");
            }
        }
        return Resultat.echec(true, "échec transitoire Gmail API");
    }

    public Resultat verifierJeton() {
        if (!isConfigured()) {
            return Resultat.echec(false,
                    "Gmail API non configurée (variables manquantes : " + variablesManquantes() + ")");
        }
        try {
            jetonAcces(false);
            return new Resultat(true, false, "jeton d'accès obtenu");
        } catch (ErreurPermanente e) {
            return Resultat.echec(false, e.getMessage());
        } catch (IOException e) {
            return Resultat.echec(true, "erreur réseau OAuth " + e.getClass().getSimpleName());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Resultat.echec(true, "diagnostic OAuth interrompu");
        }
    }

    private String jetonAcces(boolean force) throws IOException, InterruptedException {
        synchronized (tokenLock) {
            if (!force && accessToken != null
                    && System.currentTimeMillis() < accessTokenExpiresAtMs - TOKEN_EXPIRY_MARGIN_SECONDS * 1000) {
                return accessToken;
            }
            String body = "grant_type=refresh_token"
                    + "&client_id=" + enc(clientId)
                    + "&client_secret=" + enc(clientSecret)
                    + "&refresh_token=" + enc(refreshToken);
            HttpResponse<String> response = poster(tokenUrl, body,
                    "application/x-www-form-urlencoded", null);
            if (response.statusCode() == 200) {
                Matcher tokenMatcher = ACCESS_TOKEN.matcher(response.body());
                if (!tokenMatcher.find()) {
                    throw new ErreurPermanente("Réponse OAuth sans access_token");
                }
                Matcher expiryMatcher = EXPIRES_IN.matcher(response.body());
                Matcher scopeMatcher = OAUTH_SCOPE.matcher(response.body());
                if (scopeMatcher.find()
                        && !scopeAutorise(scopeMatcher.group(1))) {
                    throw new ErreurPermanente("OAuth scope gmail.send absent");
                }
                long lifetimeSeconds;
                try {
                    lifetimeSeconds = expiryMatcher.find()
                            ? Long.parseLong(expiryMatcher.group(1)) : 300;
                } catch (NumberFormatException e) {
                    throw new ErreurPermanente("Réponse OAuth avec expires_in invalide");
                }
                accessToken = tokenMatcher.group(1);
                accessTokenExpiresAtMs = System.currentTimeMillis() + lifetimeSeconds * 1000;
                return accessToken;
            }

            String error = codeErreur(response.body());
            if (response.statusCode() >= 500 || response.statusCode() == 429) {
                throw new IOException("OAuth HTTP " + response.statusCode());
            }
            if ("invalid_grant".equals(error)) {
                throw new ErreurPermanente(
                        "OAuth invalid_grant : refresh token expiré ou révoqué");
            }
            if ("invalid_client".equals(error) || "unauthorized_client".equals(error)) {
                throw new ErreurPermanente("OAuth " + error + " : client non autorisé");
            }
            throw new ErreurPermanente("OAuth HTTP " + response.statusCode()
                    + (error.isBlank() ? "" : " (" + error + ")"));
        }
    }

    private void invaliderJeton() {
        synchronized (tokenLock) {
            accessToken = null;
            accessTokenExpiresAtMs = 0;
        }
    }

    private HttpResponse<String> poster(String url, String body, String contentType, String bearer)
            throws IOException, InterruptedException {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", contentType)
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        if (bearer != null) request.header("Authorization", "Bearer " + bearer);
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private static String detailHttp(int status, String body) {
        String error = codeErreur(body);
        if (!error.isBlank()) return "HTTP " + status + " (" + error + ")";
        if (status == 401) return "HTTP 401 (jeton d'accès refusé après renouvellement)";
        if (status == 403) return "HTTP 403 (permission Gmail refusée)";
        return "HTTP " + status;
    }

    private static String codeErreur(String body) {
        if (body == null) return "";
        Matcher matcher = ERROR.matcher(body);
        if (matcher.find()) return matcher.group(1);
        matcher = ERROR_STATUS.matcher(body);
        if (matcher.find()) return matcher.group(1);
        matcher = ERROR_REASON.matcher(body);
        return matcher.find() ? matcher.group(1) : "";
    }

    private boolean adresseExpediteurValide() {
        return sender.matches("[^\\s@<>]+@[^\\s@<>]+\\.[^\\s@<>]+");
    }

    private static boolean scopeAutorise(String scopes) {
        for (String scope : scopes.split("\\s+")) {
            if ("https://www.googleapis.com/auth/gmail.send".equals(scope)) return true;
        }
        return false;
    }

    static String construireMime(String from, String to, String sujet, String texte) {
        verifierEntete("expéditeur", from);
        verifierEntete("destinataire", to);
        verifierEntete("sujet", sujet);
        if (to == null || to.isBlank() || !to.contains("@")) {
            throw new IllegalArgumentException("destinataire invalide");
        }
        String body = Base64.getMimeEncoder(76, new byte[] {'\r', '\n'})
                .encodeToString((texte == null ? "" : texte).getBytes(StandardCharsets.UTF_8));
        String encodedSubject = "=?UTF-8?B?"
                + Base64.getEncoder().encodeToString(sujet.getBytes(StandardCharsets.UTF_8)) + "?=";
        return "From: " + from + "\r\n"
                + "To: " + to + "\r\n"
                + "Subject: " + encodedSubject + "\r\n"
                + "MIME-Version: 1.0\r\n"
                + "Content-Type: text/plain; charset=UTF-8\r\n"
                + "Content-Transfer-Encoding: base64\r\n"
                + "\r\n"
                + body + "\r\n";
    }

    private static void verifierEntete(String name, String value) {
        if (value == null || value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0) {
            throw new IllegalArgumentException(name + " invalide");
        }
    }

    private static String enc(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String nettoyer(String value) {
        return value == null ? "" : value.trim();
    }

    private static final class ErreurPermanente extends RuntimeException {
        private static final long serialVersionUID = 1L;

        ErreurPermanente(String message) {
            super(message, null, false, false);
        }
    }
}
