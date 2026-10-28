package mg.manao.backend.service;

import mg.manao.backend.entity.Reservation;
import mg.manao.backend.entity.Utilisateur;
import mg.manao.backend.entity.Voyage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Envoi du rappel de voyage par WhatsApp via Evolution API (self-hosted,
 * basé sur Baileys/WhatsApp Web — https://github.com/EvolutionAPI/evolution-api).
 * Aucun template pré-approuvé nécessaire, texte libre autorisé.
 *
 * PRÉALABLE obligatoire, non automatisable depuis ce code :
 *   1. Héberger le serveur Evolution API (Docker).
 *   2. Créer une "instance" et la connecter en scannant son QR code avec le
 *      numéro WhatsApp qui enverra les rappels (comme WhatsApp Web).
 *   3. Renseigner EVOLUTION_API_URL / EVOLUTION_API_KEY / EVOLUTION_INSTANCE dans .env.
 *
 * Tant que EVOLUTION_ENABLED=false (ou que l'URL/clé sont vides), ce service
 * se contente de logguer un avertissement et NE FAIT JAMAIS échouer l'appelant.
 */
@Service
public class WhatsAppService {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppService.class);
    private static final DateTimeFormatter HEURE_FMT = DateTimeFormatter.ofPattern("HH:mm");
    private static final java.time.ZoneId FUSEAU_MADAGASCAR = java.time.ZoneId.of("Indian/Antananarivo");

    @Value("${app.evolution.enabled:false}")
    private boolean enabled;

    @Value("${app.evolution.api-url:}")
    private String apiUrl;

    @Value("${app.evolution.api-key:}")
    private String apiKey;

    @Value("${app.evolution.instance:}")
    private String instance;

    @Value("${app.evolution.default-country-code:261}")
    private String defaultCountryCode;

    // RestTemplate par défaut = timeouts infinis : un Evolution API injoignable
    // bloquerait le thread du scheduler indéfiniment. On borne à 5 s.
    RestTemplate restTemplate = new RestTemplateBuilder()
            .setConnectTimeout(Duration.ofSeconds(5))
            .setReadTimeout(Duration.ofSeconds(5))
            .build();

    /** Issue d'un envoi. ENVOYE = le serveur Evolution a accepté le message (HTTP 2xx), pas une preuve de lecture. */
    public enum Statut { ENVOYE, NON_CONFIGURE, NUMERO_INVALIDE, ECHEC }

    public record ResultatEnvoi(Statut statut, String detail) {
        public boolean estEnvoye() { return statut == Statut.ENVOYE; }
    }

    /** Vrai seulement si Evolution API est activé ET entièrement configuré. */
    public boolean estActif() {
        return enabled && notBlank(apiUrl) && notBlank(apiKey) && notBlank(instance);
    }

    private static boolean notBlank(String s) { return s != null && !s.isBlank(); }

    /** Numéro masqué pour les logs : +261 ••• •• 12 (jamais le numéro complet). */
    static String masquer(String numero) {
        if (numero == null || numero.length() < 4) return "***";
        return "***" + numero.substring(numero.length() - 2);
    }

    /**
     * Envoie le rappel. Ne lève jamais d'exception. Un échec réseau/HTTP renvoie ECHEC avec une
     * cause courte (classe d'erreur / code HTTP), sans numéro, clé API ni corps de réponse.
     */
    public ResultatEnvoi envoyerRappelVoyage(Reservation reservation) {
        if (!estActif()) {
            log.warn("Evolution API non configuré -> rappel non envoyé pour la réservation {}.", reservation.getId());
            return new ResultatEnvoi(Statut.NON_CONFIGURE, "evolution non configuré");
        }

        Utilisateur voyageur = reservation.getUtilisateur();
        Voyage voyage = reservation.getVoyage();
        String numero = normaliserNumero(voyageur == null ? null : voyageur.getTelephone());
        if (numero == null) {
            log.warn("Numéro absent ou invalide -> rappel non envoyé (réservation {}).", reservation.getId());
            return new ResultatEnvoi(Statut.NUMERO_INVALIDE, "numéro absent ou invalide");
        }

        long minutesRestantes = java.time.Duration.between(
                java.time.LocalDateTime.now(FUSEAU_MADAGASCAR),
                java.time.LocalDateTime.of(voyage.getDateDepart(), voyage.getHeureDepart())
        ).toMinutes();

        String texte = String.format(
                "🔔 Rappel de voyage%n"
                + "Bonjour %s, votre voyage vers %s est prévu à %s.%n"
                + "Votre départ est prévu dans %d minutes. Merci de vous présenter à l'avance.%n"
                + "Bonne route !",
                nvl(voyageur.getPrenom()), nvl(voyage.getVilleArrivee()),
                voyage.getHeureDepart().format(HEURE_FMT), Math.max(minutesRestantes, 0)
        );

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("number", numero);
        body.put("text", texte);

        try {
            String url = apiUrl + "/message/sendText/" + instance;
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("apikey", apiKey);
            ResponseEntity<String> rep = restTemplate.exchange(url, HttpMethod.POST, new HttpEntity<>(body, headers), String.class);
            if (rep != null && rep.getStatusCode().is2xxSuccessful()) {
                log.info("Rappel WhatsApp accepté par Evolution API pour la réservation {} ({}).",
                        reservation.getId(), masquer(numero));
                return new ResultatEnvoi(Statut.ENVOYE, "HTTP " + rep.getStatusCode().value());
            }
            String code = rep == null ? "réponse vide" : "HTTP " + rep.getStatusCode().value();
            log.error("Evolution API a refusé le rappel de la réservation {} : {}", reservation.getId(), code);
            return new ResultatEnvoi(Statut.ECHEC, code);
        } catch (RestClientResponseException e) {
            log.error("Échec rappel WhatsApp réservation {} : HTTP {}", reservation.getId(), e.getStatusCode().value());
            return new ResultatEnvoi(Statut.ECHEC, "HTTP " + e.getStatusCode().value());
        } catch (Exception e) {
            // On ne logge que la classe de l'exception : le message peut contenir l'URL ou des données.
            log.error("Échec rappel WhatsApp réservation {} : {}", reservation.getId(), e.getClass().getSimpleName());
            return new ResultatEnvoi(Statut.ECHEC, e.getClass().getSimpleName());
        }
    }

    private String normaliserNumero(String telephone) {
        if (telephone == null) return null;
        String digits = telephone.replaceAll("[^0-9]", "");
        if (digits.isEmpty()) return null;
        if (digits.startsWith(defaultCountryCode)) return digits;
        if (digits.startsWith("0")) return defaultCountryCode + digits.substring(1);
        if (digits.length() < 8) return null;
        return defaultCountryCode + digits;
    }

    private String nvl(String s) { return s == null ? "-" : s; }
}