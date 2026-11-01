package main.java.mg.manao.backend.service;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/** Teste GmailApiSender contre un faux serveur Google local : aucun appel reseau reel. */
class GmailApiSenderTest {

    private HttpServer server;
    private String base;
    private final AtomicInteger tokenCalls = new AtomicInteger();
    private final AtomicInteger sendCalls = new AtomicInteger();
    private final List<String> sendBodies = new CopyOnWriteArrayList<>();
    private final List<String> sendAuth = new CopyOnWriteArrayList<>();
    private volatile int sendStatus = 200;
    private volatile int sendStatusFirstOnly = 0;
    private volatile int tokenStatus = 200;
    private volatile String tokenBody = "{\"access_token\":\"AT-1\",\"expires_in\":3600}";

    @BeforeEach
    void demarrer() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/token", ex -> {
            tokenCalls.incrementAndGet();
            ex.getRequestBody().readAllBytes();
            reponse(ex, tokenStatus, tokenBody);
        });
        server.createContext("/send", ex -> {
            int n = sendCalls.incrementAndGet();
            sendBodies.add(new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            sendAuth.add(ex.getRequestHeaders().getFirst("Authorization"));
            int st = (n == 1 && sendStatusFirstOnly != 0) ? sendStatusFirstOnly : sendStatus;
            reponse(ex, st, st == 200 ? "{\"id\":\"msg123\",\"threadId\":\"t1\"}" : "{\"error\":{\"code\":" + st + "}}");
        });
        server.start();
        base = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void arreter() { server.stop(0); }

    private static void reponse(com.sun.net.httpserver.HttpExchange ex, int st, String body) throws java.io.IOException {
        byte[] b = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Content-Type", "application/json");
        ex.sendResponseHeaders(st, b.length);
        ex.getResponseBody().write(b);
        ex.close();
    }

    private GmailApiSender sender(boolean enabled, String refresh) {
        return new GmailApiSender(enabled, "cid", "csecret", refresh, "me@example.com", base + "/token", base + "/send");
    }

    @Test
    void desactiveParDefautNeFaitAucunAppel() {
        GmailApiSender s = sender(false, "rt");
        assertFalse(s.isEnabled());
        assertFalse(s.envoyer("a@b.c", "S", "T").succes());
        assertEquals(0, tokenCalls.get());
    }

    @Test
    void variableManquanteEstNommeeSansValeur() {
        GmailApiSender s = sender(true, "");
        assertFalse(s.isConfigured());
        assertEquals("GMAIL_OAUTH_REFRESH_TOKEN", s.variablesManquantes());
        assertFalse(s.envoyer("a@b.c", "S", "T").succes());
        assertEquals(0, tokenCalls.get());
    }

    @Test
    void envoiReussiProduitUnMimeValide() throws Exception {
        GmailApiSender s = sender(true, "rt");
        GmailApiSender.Resultat r = s.envoyer("dest@example.com", "Votre code de réinitialisation", "Bonjour é\nCode : 123456");
        assertTrue(r.succes());
        assertEquals("Bearer AT-1", sendAuth.get(0));
        String corps = sendBodies.get(0);
        String raw = corps.replaceAll("^\\{\"raw\":\"|\"}$", "");
        assertFalse(raw.contains("=") || raw.contains("+") || raw.contains("/"), "base64url sans padding");
        String mime = new String(Base64.getUrlDecoder().decode(raw), StandardCharsets.UTF_8);
        assertTrue(mime.contains("To: dest@example.com\r\n"));
        assertTrue(mime.contains("From: me@example.com\r\n"));
        assertTrue(mime.contains("Content-Type: text/plain; charset=UTF-8\r\n"));
        String b64 = mime.substring(mime.indexOf("\r\n\r\n") + 4).replace("\r\n", "");
        assertEquals("Bonjour é\nCode : 123456", new String(Base64.getDecoder().decode(b64), StandardCharsets.UTF_8));
    }

    @Test
    void jetonMisEnCacheEntreDeuxEnvois() {
        GmailApiSender s = sender(true, "rt");
        assertTrue(s.envoyer("a@b.c", "S", "T").succes());
        assertTrue(s.envoyer("a@b.c", "S", "T").succes());
        assertEquals(1, tokenCalls.get());
        assertEquals(2, sendCalls.get());
    }

    @Test
    void erreur401RenouvelleLeJetonUneFois() {
        sendStatusFirstOnly = 401;
        GmailApiSender s = sender(true, "rt");
        assertTrue(s.envoyer("a@b.c", "S", "T").succes());
        assertEquals(2, tokenCalls.get());
        assertEquals(2, sendCalls.get());
    }

    @Test
    void erreur500EstReessayeUneFois() {
        sendStatusFirstOnly = 500;
        GmailApiSender s = sender(true, "rt");
        assertTrue(s.envoyer("a@b.c", "S", "T").succes());
        assertEquals(2, sendCalls.get());
    }

    @Test
    void erreur400EstDefinitiveSansNouvelEssai() {
        sendStatus = 400;
        GmailApiSender.Resultat r = sender(true, "rt").envoyer("a@b.c", "S", "T");
        assertFalse(r.succes());
        assertFalse(r.transitoire());
        assertEquals(1, sendCalls.get());
    }

    @Test
    void invalidGrantDonneUnMessageClairSansSecret() {
        tokenStatus = 400;
        tokenBody = "{\"error\":\"invalid_grant\",\"error_description\":\"Token has been expired or revoked.\"}";
        GmailApiSender.Resultat r = sender(true, "SUPER-REFRESH-SECRET").envoyer("a@b.c", "S", "T");
        assertFalse(r.succes());
        assertTrue(r.detail().contains("invalid_grant"));
        assertFalse(r.detail().contains("SUPER-REFRESH-SECRET"));
        assertEquals(0, sendCalls.get());
    }

    @Test
    void http200SansIdN_estPasUnSucces() {
        // le faux serveur renvoie toujours un id ; on teste donc le garde-fou via un serveur dedie
        server.removeContext("/send");
        server.createContext("/send", ex -> { ex.getRequestBody().readAllBytes(); reponse(ex, 200, "{}"); });
        assertFalse(sender(true, "rt").envoyer("a@b.c", "S", "T").succes());
    }

    @Test
    void refuseInjectionEnTete() {
        GmailApiSender s = sender(true, "rt");
        assertFalse(s.envoyer("a@b.c\r\nBcc: x@y.z", "S", "T").succes());
        assertFalse(s.envoyer("a@b.c", "S\r\nBcc: x@y.z", "T").succes());
        assertEquals(0, sendCalls.get());
    }

    @Test
    void serveurInjoignableEchoueProprement() {
        server.stop(0);
        GmailApiSender.Resultat r = sender(true, "rt").envoyer("a@b.c", "S", "T");
        assertFalse(r.succes());
        assertTrue(r.transitoire());
    }
}