package main.java.mg.manao.backend.service;

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
 * Envoi d'e-mails via l'API Gmail (HTTPS, port 443) au lieu de SMTP.
 *
 * Pourquoi : Render Free bloque les connexions sortantes SMTP (ports 25/465/587).
 * L'API Gmail passe par HTTPS et n'est donc pas concernee.
 *
 * Securite :
 *  - scope necessaire : gmail.send uniquement (aucune lecture de boite mail) ;
 *  - client id / secret / refresh token viennent UNIQUEMENT de variables d'environnement ;
 *  - aucun secret, aucun jeton d'acces et aucun contenu de message n'est ecrit dans les logs.
 *
 * Active uniquement si GMAIL_API_ENABLED=true (indicateur distinct d'EMAIL_ENABLED).
 */
@Component
public class GmailApiSender {

    private static final Pattern ACCESS_TOKEN = Pattern.compile("\"access_token\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern EXPIRES_IN = Pattern.compile("\"expires_in\"\\s*:\\s*(\\d+)");
    private static final Pattern ERROR = Pattern.compile("\"error\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern MESSAGE_ID = Pattern.compile("\"id\"\\s*:\\s*\"([^\"]+)\"");

    /** Marge avant expiration : on renouvelle le jeton d'acces 60 s plus tot. */
    private static final long MARGE_SECONDES = 60;

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
    private long accessTokenExpireAtMs;

    /** Resultat d'un envoi : succes uniquement si Google a renvoye HTTP 200 avec l'id du message. */
    public record Resultat(boolean succes, boolean transitoire, String detail) {
        static Resultat ok(String id) { return new Resultat(true, false, "messageId=" + id); }
        static Resultat echec(boolean transitoire, String detail) { return new Resultat(false, transitoire, detail); }
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

    /** true si l'indicateur est actif ET les 4 valeurs obligatoires sont presentes. */
    public boolean isConfigured() {
        return enabled && !clientId.isEmpty() && !clientSecret.isEmpty()
                && !refreshToken.isEmpty() && !sender.isEmpty();
    }

    /** Liste des variables manquantes (noms seulement, jamais les valeurs). */
    public String variablesManquantes() {
        StringBuilder sb = new StringBuilder();
        if (clientId.isEmpty()) sb.append("GMAIL_OAUTH_CLIENT_ID ");
        if (clientSecret.isEmpty()) sb.append("GMAIL_OAUTH_CLIENT_SECRET ");
        if (refreshToken.isEmpty()) sb.append("GMAIL_OAUTH_REFRESH_TOKEN ");
        if (sender.isEmpty()) sb.append("GMAIL_SENDER_ADDRESS ");
        return sb.toString().trim();
    }

    /**
     * Envoie un e-mail texte brut. Jusqu'a 2 essais pour les erreurs transitoires
     * (reseau, 429, 5xx) ; un 401 force un renouvellement du jeton d'acces ;
     * toute autre erreur 4xx est definitive (pas de nouvel essai).
     */
    public Resultat envoyer(String destinataire, String sujet, String texte) {
        if (!isConfigured()) {
            return Resultat.echec(false, "Gmail API non configuree (variables manquantes : " + variablesManquantes() + ")");
        }
        final String raw;
        try {
            raw = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(construireMime(sender, destinataire, sujet, texte).getBytes(StandardCharsets.UTF_8));
        } catch (IllegalArgumentException e) {
            return Resultat.echec(false, e.getMessage());
        }

        Resultat dernier = Resultat.echec(true, "aucune tentative");
        boolean tokenRenouvele = false;
        for (int tentative = 1; tentative <= 2; tentative++) {
            try {
                String token = jetonAcces(false);
                HttpResponse<String> r = poster(sendUrl, "{\"raw\":\"" + raw + "\"}", "application/json", token);
                int code = r.statusCode();
                if (code == 200) {
                    Matcher m = MESSAGE_ID.matcher(r.body());
                    if (m.find()) return Resultat.ok(m.group(1));
                    return Resultat.echec(false, "HTTP 200 sans identifiant de message : envoi non confirme");
                }
                if (code == 401 && !tokenRenouvele) {
                    tokenRenouvele = true;
                    invaliderJeton();
                    dernier = Resultat.echec(true, "HTTP 401 : jeton d'acces refuse, renouvellement");
                    tentative--; // ce renouvellement ne consomme pas un essai
                    continue;
                }
                boolean transitoire = code == 429 || code >= 500;
                dernier = Resultat.echec(transitoire, "HTTP " + code + " " + codeErreur(r.body()));
                if (!transitoire) return dernier;
            } catch (ErreurPermanente e) {
                return Resultat.echec(false, e.getMessage());
            } catch (IOException e) {
                dernier = Resultat.echec(true, "erreur reseau " + e.getClass().getSimpleName());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return Resultat.echec(true, "envoi interrompu");
            }
            if (tentative < 2) {
                try {
                    Thread.sleep(800);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return Resultat.echec(true, "envoi interrompu");
                }
            }
        }
        return dernier;
    }

    /** Diagnostic sans envoi : tente seulement d'obtenir un jeton d'acces. */
    public Resultat verifierJeton() {
        if (!isConfigured()) {
            return Resultat.echec(false, "Gmail API non configuree (variables manquantes : " + variablesManquantes() + ")");
        }
        try {
            jetonAcces(true);
            return new Resultat(true, false, "jeton d'acces obtenu");
        } catch (ErreurPermanente e) {
            return Resultat.echec(false, e.getMessage());
        } catch (IOException e) {
            return Resultat.echec(true, "erreur reseau " + e.getClass().getSimpleName());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Resultat.echec(true, "interrompu");
        }
    }

    // ------------------------------------------------------------------ OAuth

    private String jetonAcces(boolean forcer) throws IOException, InterruptedException {
        synchronized (tokenLock) {
            if (!forcer && accessToken != null
                    && System.currentTimeMillis() < accessTokenExpireAtMs - MARGE_SECONDES * 1000) {
                return accessToken;
            }
            String corps = "grant_type=refresh_token"
                    + "&client_id=" + enc(clientId)
                    + "&client_secret=" + enc(clientSecret)
                    + "&refresh_token=" + enc(refreshToken);
            HttpResponse<String> r = poster(tokenUrl, corps, "application/x-www-form-urlencoded", null);
            if (r.statusCode() == 200) {
                Matcher t = ACCESS_TOKEN.matcher(r.body());
                if (!t.find()) throw new ErreurPermanente("Reponse OAuth sans access_token");
                Matcher e = EXPIRES_IN.matcher(r.body());
                long secondes = e.find() ? Long.parseLong(e.group(1)) : 300;
                accessToken = t.group(1);
                accessTokenExpireAtMs = System.currentTimeMillis() + secondes * 1000;
                return accessToken;
            }
            String err = codeErreur(r.body());
            if (r.statusCode() >= 500 || r.statusCode() == 429) {
                throw new IOException("OAuth HTTP " + r.statusCode());
            }
            if ("invalid_grant".equals(err)) {
                throw new ErreurPermanente("OAuth invalid_grant : refresh token expire ou revoque. "
                        + "Verifier que l'application est 'En production' dans Google Cloud, "
                        + "puis regenerer le refresh token");
            }
            if ("invalid_client".equals(err) || "unauthorized_client".equals(err)) {
                throw new ErreurPermanente("OAuth " + err + " : client id/secret incorrects ou client non autorise");
            }
            throw new ErreurPermanente("OAuth HTTP " + r.statusCode() + " " + err);
        }
    }

    private void invaliderJeton() {
        synchronized (tokenLock) {
            accessToken = null;
            accessTokenExpireAtMs = 0;
        }
    }

    // ------------------------------------------------------------------- HTTP

    private HttpResponse<String> poster(String url, String corps, String contentType, String bearer)
            throws IOException, InterruptedException {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", contentType)
                .POST(HttpRequest.BodyPublishers.ofString(corps, StandardCharsets.UTF_8));
        if (bearer != null) b.header("Authorization", "Bearer " + bearer);
        return http.send(b.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private static String codeErreur(String body) {
        if (body == null) return "";
        Matcher m = ERROR.matcher(body);
        return m.find() ? m.group(1) : "";
    }

    // ------------------------------------------------------------------- MIME

    /**
     * Message RFC 2822 minimal : en-tetes ASCII (sujet encode RFC 2047), corps UTF-8 en base64.
     * Refuse tout retour a la ligne dans les en-tetes (injection d'en-tetes).
     */
    static String construireMime(String from, String to, String sujet, String texte) {
        verifierEntete("expediteur", from);
        verifierEntete("destinataire", to);
        verifierEntete("sujet", sujet);
        if (to == null || to.isBlank() || !to.contains("@")) {
            throw new IllegalArgumentException("destinataire invalide");
        }
        String corps = Base64.getMimeEncoder(76, new byte[] {'\r', '\n'})
                .encodeToString((texte == null ? "" : texte).getBytes(StandardCharsets.UTF_8));
        String sujetEncode = "=?UTF-8?B?"
                + Base64.getEncoder().encodeToString(sujet.getBytes(StandardCharsets.UTF_8)) + "?=";
        return "From: " + from + "\r\n"
                + "To: " + to + "\r\n"
                + "Subject: " + sujetEncode + "\r\n"
                + "MIME-Version: 1.0\r\n"
                + "Content-Type: text/plain; charset=UTF-8\r\n"
                + "Content-Transfer-Encoding: base64\r\n"
                + "\r\n"
                + corps + "\r\n";
    }

    private static void verifierEntete(String nom, String valeur) {
        if (valeur == null || valeur.indexOf('\r') >= 0 || valeur.indexOf('\n') >= 0) {
            throw new IllegalArgumentException(nom + " invalide");
        }
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    private static String nettoyer(String s) {
        return s == null ? "" : s.trim();
    }

    /** Erreur definitive (inutile de reessayer). */
    private static final class ErreurPermanente extends RuntimeException {
        private static final long serialVersionUID = 1L;
        ErreurPermanente(String message) { super(message, null, false, false); }
    }
}