package mg.manao.backend.service;

import mg.manao.backend.entity.Reservation;
import mg.manao.backend.entity.Utilisateur;
import mg.manao.backend.entity.Voyage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;

/**
 * Envoi du rappel de voyage par e-mail (Gmail SMTP via spring-boot-starter-mail).
 *
 * Tant que EMAIL_ENABLED=false (ou que le username/mot de passe sont vides), ce
 * service se contente de logguer un avertissement et NE FAIT JAMAIS échouer l'appelant,
 * sur le même principe que WhatsAppService.
 */
@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);
    private static final DateTimeFormatter HEURE_FMT = DateTimeFormatter.ofPattern("HH:mm");

    private final JavaMailSender mailSender;

    @Value("${app.email.enabled:false}")
    private boolean enabled;

    @Value("${spring.mail.username:}")
    private String username;

    @Value("${spring.mail.password:}")
    private String password;

    @Value("${app.email.from:}")
    private String from;

    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    public boolean envoyerRappelVoyage(Reservation reservation) {
        if (!enabled || username == null || username.isBlank() || password == null || password.isBlank()) {
            log.warn("Email non configuré (app.email.enabled=false ou GMAIL_USERNAME/GMAIL_APP_PASSWORD manquants) "
                    + "-> rappel non envoyé pour la réservation {}.", reservation.getId());
            return false;
        }

        Utilisateur voyageur = reservation.getUtilisateur();
        Voyage voyage = reservation.getVoyage();

        String emailDestinataire = voyageur.getEmail();
        if (emailDestinataire == null || emailDestinataire.isBlank()) {
            log.warn("Adresse e-mail absente pour l'utilisateur {} -> rappel non envoyé (réservation {}).",
                    voyageur.getId(), reservation.getId());
            return false;
        }

        long minutesRestantes = java.time.Duration.between(
                java.time.LocalDateTime.now(java.time.ZoneId.of("Indian/Antananarivo")),
                java.time.LocalDateTime.of(voyage.getDateDepart(), voyage.getHeureDepart())
        ).toMinutes();

        String texte = String.format(
                "Bonjour %s,%n%n"
                + "Votre voyage vers %s est prévu à %s.%n"
                + "Votre départ est prévu dans %d minutes. Merci de vous présenter à l'avance.%n%n"
                + "Bonne route !",
                nvl(voyageur.getPrenom()), nvl(voyage.getVilleArrivee()),
                voyage.getHeureDepart().format(HEURE_FMT), Math.max(minutesRestantes, 0)
        );

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(from == null || from.isBlank() ? username : from);
            message.setTo(emailDestinataire);
            message.setSubject("🔔 Rappel de voyage");
            message.setText(texte);
            mailSender.send(message);
            log.info("Rappel e-mail envoyé pour la réservation {} ({}).", reservation.getId(), emailDestinataire);
            return true;
        } catch (Exception e) {
            log.error("Échec de l'envoi du rappel e-mail pour la réservation {} : {}",
                    reservation.getId(), e.getMessage());
            return false;
        }
    }

    public boolean envoyerConfirmationReservation(Reservation reservation) {
        if (!enabled || username == null || username.isBlank() || password == null || password.isBlank()) {
            log.warn("Email non configuré (app.email.enabled=false ou GMAIL_USERNAME/GMAIL_APP_PASSWORD manquants) "
                    + "-> confirmation non envoyée pour la réservation {}.", reservation.getId());
            return false;
        }

        Utilisateur voyageur = reservation.getUtilisateur();
        Voyage voyage = reservation.getVoyage();

        String emailDestinataire = voyageur.getEmail();
        if (emailDestinataire == null || emailDestinataire.isBlank()) {
            log.warn("Adresse e-mail absente pour l'utilisateur {} -> confirmation non envoyée (réservation {}).",
                    voyageur.getId(), reservation.getId());
            return false;
        }

        String texte = String.format(
                "Bonjour %s,%n%n"
                + "Votre réservation a été validée !%n%n"
                + "Trajet : %s -> %s%n"
                + "Date : %s%n"
                + "Heure de départ : %s%n"
                + "Place N° : %s%n%n"
                + "Merci de votre confiance, bon voyage !",
                nvl(voyageur.getPrenom()), nvl(voyage.getVilleDepart()), nvl(voyage.getVilleArrivee()),
                voyage.getDateDepart(), voyage.getHeureDepart().format(HEURE_FMT),
                reservation.getNumeroPlace() == null ? "-" : reservation.getNumeroPlace().toString()
        );

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(from == null || from.isBlank() ? username : from);
            message.setTo(emailDestinataire);
            message.setSubject("✅ Confirmation de votre réservation");
            message.setText(texte);
            mailSender.send(message);
            log.info("Confirmation e-mail envoyée pour la réservation {} ({}).", reservation.getId(), emailDestinataire);
            return true;
        } catch (Exception e) {
            log.error("Échec de l'envoi de la confirmation e-mail pour la réservation {} : {}",
                    reservation.getId(), e.getMessage());
            return false;
        }
    }

    private String nvl(String s) { return s == null ? "-" : s; }
}