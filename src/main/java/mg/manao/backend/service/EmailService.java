package mg.manao.backend.service;

import mg.manao.backend.entity.Reservation;
import mg.manao.backend.exception.ApiException;
import jakarta.mail.AuthenticationFailedException;
import jakarta.mail.MessagingException;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailSendException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;

import javax.net.ssl.SSLException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

/**
 * Gmail API est exclusivement utilisée pour les codes de réinitialisation.
 * SMTP reste disponible uniquement pour les diagnostics et e-mails de test administratifs.
 * Les rappels de voyage passent par WhatsApp.
 *
 * Aucun secret et jamais le code de reinitialisation dans les logs.
 */
@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);
    private static final int MAX_DIAGNOSTIC_CAUSES = 12;

    /** « ada@example.com » -> « a***@example.com » : les logs ne doivent pas contenir d'adresses completes. */
    static String masquerEmail(String email) {
        if (email == null || email.isBlank()) return "-";
        int at = email.indexOf('@');
        if (at <= 0) return "***";
        return email.charAt(0) + "***" + email.substring(at);
    }

    private final JavaMailSender mailSender;
    private final GmailApiSender gmailApi;

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

    public EmailService(JavaMailSender mailSender, GmailApiSender gmailApi) {
        this.mailSender = mailSender;
        this.gmailApi = gmailApi;
    }

    private boolean smtpConfigure() {
        return enabled && username != null && !username.isBlank()
                && password != null && !password.isBlank();
    }

    // ------------------------------------------------------------ diagnostics

    public EmailDiagnostic diagnostiquerConnexion() {
        if (gmailApi.isEnabled()) {
            GmailApiSender.Resultat r = gmailApi.verifierJeton();
            if (!r.succes()) log.warn("Echec du diagnostic Gmail API : {}", r.detail());
            return r.succes()
                    ? new EmailDiagnostic(true, "Gmail API : jeton d'acces obtenu.")
                    : new EmailDiagnostic(false, "Gmail API : connexion impossible.");
        }
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
        if (gmailApi.isEnabled()) {
            GmailApiSender.Resultat r = gmailApi.envoyer(destinataire, "E-mail de test MANAO",
                    "Cet e-mail confirme que l'envoi par l'API Gmail de MANAO fonctionne.");
            if (!r.succes()) log.warn("Échec de l'e-mail de test Gmail API à {} : {}", masquerEmail(destinataire), r.detail());
            return r.succes()
                    ? new EmailDiagnostic(true, "E-mail de test envoyé.")
                    : new EmailDiagnostic(false, "E-mail de test non envoyé.");
        }
        if (!smtpConfigure()) {
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
            log.warn("Échec de l'envoi de l'e-mail de test à {} : {}", masquerEmail(destinataire), diagnosticSmtpCause(e));
            return new EmailDiagnostic(false, "E-mail de test non envoyé.");
        }
    }

    // ------------------------------------------- e-mails supprimes (no-op)

    /** @deprecated Decision metier : plus de rappel par e-mail (WhatsApp uniquement). Ne fait rien. */
    @Deprecated
    public void envoyerRappelVoyage(Reservation reservation) {
        log.debug("Rappel e-mail desactive (rappels via WhatsApp uniquement).");
    }

    /** @deprecated Decision metier : plus de confirmation de reservation par e-mail. Ne fait rien. */
    @Deprecated
    public void envoyerConfirmationReservation(Reservation reservation) {
        log.debug("Confirmation e-mail desactivee.");
    }

    /** @deprecated Decision metier : pas de verification d'e-mail a l'inscription. Ne fait rien, renvoie false. */
    @Deprecated
    public boolean envoyerCodeVerification(String destinataire, String prenom, String code, String lienVerification) {
        log.debug("Verification d'e-mail a l'inscription desactivee.");
        return false;
    }

    // ------------------------------------------------ code de reinitialisation

    /**
     * Envoie le code de reinitialisation, de facon SYNCHRONE (AuthService ne stocke le hash
     * que si l'envoi a reussi). Le code n'est jamais renvoye par HTTP ni ecrit dans les logs.
     *
     * @return true seulement si le fournisseur a accepte le message.
     */
    public boolean envoyerCodeReinitialisation(String destinataire, String prenom, String code) {
        verifierConfigurationGmailApi();
        String texte = String.format(
                "Bonjour %s,%n%n"
                        + "Vous avez demandé la réinitialisation du mot de passe de votre compte "
                        + "Réservation en ligne à Madagascar.%n%n"
                        + "Votre code de vérification :%n%n"
                        + "    %s%n%n"
                        + "Ce code expire dans 30 minutes et ne peut être utilisé qu'une seule fois.%n"
                        + "Si vous n'êtes pas à l'origine de cette demande, ignorez cet e-mail "
                        + "et ne communiquez ce code à personne.%n%n"
                        + "Réservation en ligne à Madagascar",
                nvl(prenom), code);

        GmailApiSender.Resultat resultat = gmailApi.envoyer(destinataire,
                "Réinitialisation de votre mot de passe – Réservation en ligne à Madagascar", texte);
        if (resultat.succes()) {
            log.info("Gmail API a accepté le code de réinitialisation pour {}.",
                    masquerEmail(destinataire));
            return true;
        }
        log.error("Échec d'envoi du code de réinitialisation via Gmail API pour {} : {}.",
                masquerEmail(destinataire), resultat.detail());
        return false;
    }

    /**
     * Vérifie la configuration avant toute recherche de compte afin que les erreurs
     * techniques ne changent pas la réponse générique selon l'existence de l'adresse.
     */
    public void verifierConfigurationReinitialisation() {
        verifierConfigurationGmailApi();
        GmailApiSender.Resultat resultat = gmailApi.verifierJeton();
        if (!resultat.succes()) {
            log.error("Gmail API indisponible pour la réinitialisation : {}.", resultat.detail());
            throw erreurGmailIndisponible();
        }
    }

    private void verifierConfigurationGmailApi() {
        if (!gmailApi.isEnabled()) {
            log.error("Gmail API désactivée : réinitialisation du mot de passe indisponible.");
            throw erreurGmailIndisponible();
        }
        if (!gmailApi.isConfigured()) {
            log.error("Configuration Gmail API incomplète (variables manquantes : {}).",
                    gmailApi.variablesManquantes());
            throw erreurGmailIndisponible();
        }
    }

    private static ApiException erreurGmailIndisponible() {
        return new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                "Le service de réinitialisation est temporairement indisponible.");
    }

    // ------------------------------------------------------------ diagnostic

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

    private String nvl(String s) { return s == null || s.isBlank() ? "-" : s; }
}