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
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

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

    private final RestTemplate restTemplate = new RestTemplate();

    public boolean envoyerRappelVoyage(Reservation reservation) {
        if (!enabled || apiUrl == null || apiUrl.isBlank() || apiKey == null || apiKey.isBlank()
                || instance == null || instance.isBlank()) {
            log.warn("Evolution API non configuré (app.evolution.enabled=false ou url/clé/instance manquants) "
                    + "-> rappel non envoyé pour la réservation {}.", reservation.getId());
            return false;
        }

        Utilisateur voyageur = reservation.getUtilisateur();
        Voyage voyage = reservation.getVoyage();
        String numero = normaliserNumero(voyageur.getTelephone());
        if (numero == null) {
            log.warn("Numéro de téléphone absent ou invalide pour l'utilisateur {} -> rappel non envoyé (réservation {}).",
                    voyageur.getId(), reservation.getId());
            return false;
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
            restTemplate.exchange(url, HttpMethod.POST, new HttpEntity<>(body, headers), String.class);
            log.info("Rappel WhatsApp (Evolution API) envoyé pour la réservation {} ({}).", reservation.getId(), numero);
            return true;
        } catch (Exception e) {
            log.error("Échec de l'envoi du rappel WhatsApp (Evolution API) pour la réservation {} : {}",
                    reservation.getId(), e.getMessage());
            return false;
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