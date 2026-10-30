package mg.manao.backend.service;

import mg.manao.backend.entity.Reservation;
import mg.manao.backend.entity.Utilisateur;
import mg.manao.backend.entity.Voyage;
import jakarta.mail.AuthenticationFailedException;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailSendException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import javax.net.ssl.SSLException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

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
    private static final int MAX_DIAGNOSTIC_CAUSES = 12;

    /** « ada@example.com » -> « a***@example.com » : les logs ne doivent pas contenir d'adresses complètes. */
    static String masquerEmail(String email) {
        if (email == null || email.isBlank()) return "-";
        int at = email.indexOf('@');
        if (at <= 0) return "***";
        return email.charAt(0) + "***" + email.substring(at);
    }
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

    public record EmailDiagnostic(boolean succes, String message) {
    }

    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    public EmailDiagnostic diagnostiquerConnexion() {
        if (!enabled) {
            return new EmailDiagnostic(false, "L'envoi d'e-mails est désactivé.");
        }
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            return new EmailDiagnostic(false, "La configuration SMTP est incomplète.");
        }

        try {
            if (mailSender instanceof JavaMailSenderImpl sender) {
                sender.testConnection();
                return new EmailDiagnostic(true, "Connexion SMTP établie.");
            }
            return new EmailDiagnostic(false, "Le fournisseur SMTP ne permet pas le test de connexion.");
        } catch (Exception e) {
            log.warn("Échec du diagnostic SMTP : {}", diagnosticSmtpCause(e));
            return new EmailDiagnostic(false, "Connexion SMTP impossible.");
        }
    }

    public EmailDiagnostic envoyerEmailTest(String destinataire) {
        if (destinataire == null || destinataire.isBlank()) {
            return new EmailDiagnostic(false, "Le destinataire est obligatoire.");
        }
        if (!enabled || username == null || username.isBlank() || password == null || password.isBlank()) {
            return new EmailDiagnostic(false, "La configuration SMTP est incomplète.");
        }

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(from == null || from.isBlank() ? username : from);
            message.setTo(destinataire);
            message.setSubject("E-mail de test MANAO");
            message.setText("Cet e-mail confirme que la configuration SMTP de MANAO fonctionne.");
            mailSender.send(message);
            return new EmailDiagnostic(true, "E-mail de test envoyé.");
        } catch (Exception e) {
            log.warn("Échec de l'envoi de l'e-mail de test à {} : {}", masquerEmail(destinataire), e.getClass().getSimpleName());
            return new EmailDiagnostic(false, "E-mail de test non envoyé.");
        }
    }

    @Async("notificationExecutor")
    public void envoyerRappelVoyage(Reservation reservation) {
        if (!enabled || username == null || username.isBlank() || password == null || password.isBlank()) {
            log.warn("Email non configuré (app.email.enabled=false ou GMAIL_USERNAME/GMAIL_APP_PASSWORD manquants) "
                    + "-> rappel non envoyé pour la réservation {}.", reservation.getId());
            return;
        }

        Utilisateur voyageur = reservation.getUtilisateur();
        Voyage voyage = reservation.getVoyage();

        String emailDestinataire = voyageur.getEmail();
        if (emailDestinataire == null || emailDestinataire.isBlank()) {
            log.warn("Adresse e-mail absente pour l'utilisateur {} -> rappel non envoyé (réservation {}).",
                    voyageur.getId(), reservation.getId());
            return;
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
            log.info("Rappel e-mail envoyé pour la réservation {} ({}).", reservation.getId(), masquerEmail(emailDestinataire));
        } catch (Exception e) {
            log.error("Échec de l'envoi du rappel e-mail pour la réservation {} : {}",
                    reservation.getId(), diagnosticSmtpCause(e));
        }
    }

    @Async("notificationExecutor")
    public void envoyerConfirmationReservation(Reservation reservation) {
        if (!enabled || username == null || username.isBlank() || password == null || password.isBlank()) {
            log.warn("Email non configuré (app.email.enabled=false ou GMAIL_USERNAME/GMAIL_APP_PASSWORD manquants) "
                    + "-> confirmation non envoyée pour la réservation {}.", reservation.getId());
            return;
        }

        Utilisateur voyageur = reservation.getUtilisateur();
        Voyage voyage = reservation.getVoyage();

        String emailDestinataire = voyageur.getEmail();
        if (emailDestinataire == null || emailDestinataire.isBlank()) {
            log.warn("Adresse e-mail absente pour l'utilisateur {} -> confirmation non envoyée (réservation {}).",
                    voyageur.getId(), reservation.getId());
            return;
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
            log.info("Confirmation e-mail envoyée pour la réservation {} ({}).", reservation.getId(), masquerEmail(emailDestinataire));
        } catch (Exception e) {
            log.error("Échec de l'envoi de la confirmation e-mail pour la réservation {} : {}",
                    reservation.getId(), diagnosticSmtpCause(e));
        }
    }

    /**
     * E-mail de vérification d'adresse (double opt-in — voir
     * EmailVerificationService). Envoyé de façon SYNCHRONE (pas d'@Async) :
     * contrairement aux rappels/confirmations de réservation, l'inscription
     * doit pouvoir dire tout de suite à l'utilisateur "vérifiez votre
     * boîte" plutôt que de répondre avec succès avant même de savoir si
     * l'envoi a marché.
     *
     * En HTML plutôt qu'en texte brut (contrairement aux autres méthodes de
     * cette classe) : c'est la fiabilité la plus déterminante ici — un
     * e-mail HTML correctement formé ne subit jamais le retour à la ligne
     * intempestif qui, en texte brut, pouvait couper le lien ou tronquer le
     * code selon le client mail. Une nouvelle tentative automatique est
     * aussi effectuée en cas d'échec SMTP passager (Gmail peut timeout
     * ponctuellement sans que ce soit un vrai problème de configuration).
     *
     * @return true si l'e-mail a été transmis avec succès au serveur SMTP.
     */
    public boolean envoyerCodeVerification(String destinataire, String prenom, String code, String lienVerification) {
        if (!enabled || username == null || username.isBlank() || password == null || password.isBlank()) {
            log.warn("Email non configuré (app.email.enabled=false ou GMAIL_USERNAME/GMAIL_APP_PASSWORD manquants) "
                    + "-> code de vérification non envoyé à {}.", masquerEmail(destinataire));
            return false;
        }

        String html = String.format(
                "<div style=\"font-family:Arial,Helvetica,sans-serif;color:#1e293b;max-width:480px;margin:auto;\">"
                + "<p>Bonjour %s,</p>"
                + "<p>Merci de votre inscription ! Pour confirmer que cette adresse e-mail vous appartient bien, "
                + "utilisez le code suivant :</p>"
                + "<div style=\"font-size:32px;font-weight:700;letter-spacing:8px;text-align:center;"
                + "background:#f1f5f9;border-radius:10px;padding:18px 0;margin:20px 0;color:#0f172a;\">%s</div>"
                + "<p style=\"text-align:center;\">"
                + "<a href=\"%s\" style=\"display:inline-block;background:#059669;color:#ffffff;"
                + "text-decoration:none;padding:10px 22px;border-radius:8px;font-weight:600;\">"
                + "Vérifier mon adresse</a></p>"
                + "<p style=\"font-size:13px;color:#64748b;\">Ce code est valable 30 minutes. "
                + "Si vous n'êtes pas à l'origine de cette inscription, ignorez simplement cet e-mail.</p>"
                + "</div>",
                nvl(prenom), code, lienVerification
        );
        String texteBrut = String.format(
                "Bonjour %s,%n%n"
                + "Merci de votre inscription ! Votre code de vérification est : %s%n%n"
                + "Ou cliquez sur ce lien : %s%n%n"
                + "Ce code est valable 30 minutes.",
                nvl(prenom), code, lienVerification
        );

        return envoyerAvecNouvellesTentatives(destinataire, () -> {
            MimeMessage mime = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mime, true, "UTF-8");
            helper.setFrom(from == null || from.isBlank() ? username : from);
            helper.setTo(destinataire);
            helper.setSubject("Votre code de vérification : " + code);
            helper.setText(texteBrut, html);
            mailSender.send(mime);
        });
    }

    /**
     * Une nouvelle tentative après un court délai en cas d'échec SMTP
     * passager (timeout, connexion refusée momentanément...) — évite qu'une
     * simple lenteur réseau ponctuelle empêche l'inscription d'aboutir.
     */
    private boolean envoyerAvecNouvellesTentatives(String destinataire, EnvoiMail envoi) {
        final int tentativesMax = 2;
        for (int tentative = 1; tentative <= tentativesMax; tentative++) {
            try {
                envoi.executer();
                log.info("E-mail envoyé à {} (tentative {}/{}).", masquerEmail(destinataire), tentative, tentativesMax);
                return true;
            } catch (Exception e) {
                log.warn("Tentative {}/{} d'envoi à {} échouée : {}",
                        tentative, tentativesMax, masquerEmail(destinataire), diagnosticSmtpCause(e));
                if (tentative == tentativesMax) {
                    log.error("Échec définitif de l'envoi à {} : {}",
                            masquerEmail(destinataire), diagnosticSmtpCause(e));
                    return false;
                }
                try {
                    Thread.sleep(800);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
        }
        return false;
    }

    static String diagnosticSmtpCause(Throwable failure) {
        if (failure == null) return "cause unavailable";

        Deque<Throwable> pending = new ArrayDeque<>();
        Set<Throwable> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        List<String> causes = new ArrayList<>();
        pending.add(failure);

        while (!pending.isEmpty() && causes.size() < MAX_DIAGNOSTIC_CAUSES) {
            Throwable current = pending.removeFirst();
            if (!visited.add(current)) continue;

            String type = current.getClass().getSimpleName();
            String category = categorieSmtp(current);
            causes.add(category == null ? type : type + " (" + category + ")");

            Throwable cause = current.getCause();
            if (cause != null && cause != current) pending.addLast(cause);

            if (current instanceof MessagingException messagingException) {
                Exception nextException = messagingException.getNextException();
                if (nextException != null && nextException != current) pending.addLast(nextException);
            }

            if (current instanceof MailSendException mailSendException
                    && mailSendException.getFailedMessages() != null) {
                mailSendException.getFailedMessages().values().stream()
                        .filter(exception -> exception != null && exception != current)
                        .forEach(pending::addLast);
            }
        }

        if (!pending.isEmpty()) causes.add("cause chain truncated");
        return String.join(" -> ", causes);
    }

    private static String categorieSmtp(Throwable failure) {
        if (failure instanceof MailAuthenticationException
                || failure instanceof AuthenticationFailedException) {
            return "SMTP authentication rejected";
        }
        if (failure instanceof ConnectException) return "connection refused";
        if (failure instanceof SocketTimeoutException) return "timeout";
        if (failure instanceof SSLException) return "TLS/SSL failure";
        if (failure instanceof UnknownHostException) return "DNS resolution failure";
        return null;
    }

    @FunctionalInterface
    private interface EnvoiMail {
        void executer() throws Exception;
    }

    private String nvl(String s) { return s == null ? "-" : s; }

    /**
     * Sends a reset code synchronously. The code is never returned over HTTP.
     */
    public boolean envoyerCodeReinitialisation(String destinataire, String prenom, String code) {
        if (!enabled || username == null || username.isBlank() || password == null || password.isBlank()) {
            log.warn("Email non configure -> reset code not sent.");
            return false;
        }
        String texte = String.format(
                "Bonjour %s,%n%nVotre code de reinitialisation est : %s%n%n"
                        + "Il est valable 30 minutes et ne peut etre utilise qu'une seule fois. "
                        + "Si vous n'etes pas a l'origine de cette demande, ignorez cet e-mail.",
                nvl(prenom), code);
        return envoyerAvecNouvellesTentatives(destinataire, () -> {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(from == null || from.isBlank() ? username : from);
            message.setTo(destinataire);
            message.setSubject("Votre code de reinitialisation");
            message.setText(texte);
            mailSender.send(message);
        });
    }
}