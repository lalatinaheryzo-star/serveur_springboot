package mg.manao.backend.service;

import mg.manao.backend.entity.Reservation;
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
 * E-mails de l'application : UNIQUEMENT le code de reinitialisation du mot de passe.
 * (Pas d'e-mail de verification a l'inscription, de confirmation de reservation ni de
 * rappel de voyage : les rappels passent par WhatsApp.)
 *
 * Deux canaux d'envoi, choisis explicitement (jamais de bascule silencieuse) :
 *  - GMAIL_API_ENABLED=true  -> API Gmail en HTTPS (indispensable sur Render Free,
 *                               qui bloque SMTP 25/465/587) ; EMAIL_ENABLED n'est pas consulte ;
 *  - sinon                   -> SMTP (spring-boot-starter-mail), soumis a EMAIL_ENABLED
 *                               et a GMAIL_USERNAME / GMAIL_APP_PASSWORD, comme avant.
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
        String texte = String.format(
                "Bonjour %s,%n%nVotre code de reinitialisation est : %s%n%n"
                        + "Il est valable 30 minutes et ne peut etre utilise qu'une seule fois. "
                        + "Si vous n'etes pas a l'origine de cette demande, ignorez cet e-mail.",
                nvl(prenom), code);

        if (gmailApi.isEnabled()) {
            return envoyerParGmailApi(destinataire, texte);
        }
        if (!smtpConfigure()) {
            log.warn("Email non configure -> reset code not sent.");
            return false;
        }
        return envoyerParSmtp(destinataire, texte);
    }

    private boolean envoyerParGmailApi(String destinataire, String texte) {
        if (!gmailApi.isConfigured()) {
            log.error("Gmail API activee mais incomplete (variables manquantes : {}) -> code non envoye.",
                    gmailApi.variablesManquantes());
            return false;
        }
        GmailApiSender.Resultat r = gmailApi.envoyer(destinataire, "Votre code de reinitialisation", texte);
        if (r.succes()) {
            log.info("Code de reinitialisation envoye via Gmail API a {} ({}).", masquerEmail(destinataire), r.detail());
            return true;
        }
        log.error("Echec definitif de l'envoi Gmail API a {} : {}", masquerEmail(destinataire), r.detail());
        return false;
    }

    private boolean envoyerParSmtp(String destinataire, String texte) {
        final int tentativesMax = 2;
        for (int tentative = 1; tentative <= tentativesMax; tentative++) {
            try {
                SimpleMailMessage message = new SimpleMailMessage();
                message.setFrom(from == null || from.isBlank() ? username : from);
                message.setTo(destinataire);
                message.setSubject("Votre code de reinitialisation");
                message.setText(texte);
                mailSender.send(message);
                log.info("E-mail envoyé à {} (tentative {}/{}).", masquerEmail(destinataire), tentative, tentativesMax);
                return true;
            } catch (Exception e) {
                log.warn("Tentative {}/{} d'envoi à {} échouée : {}",
                        tentative, tentativesMax, masquerEmail(destinataire), diagnosticSmtpCause(e));
                if (tentative == tentativesMax) {
                    log.error("Échec définitif de l'envoi à {} : {}", masquerEmail(destinataire), diagnosticSmtpCause(e));
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

    private String nvl(String s) { return s == null ? "-" : s; }
}