package mg.manao.backend.service;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.core.read.ListAppender;
import jakarta.mail.AuthenticationFailedException;
import jakarta.mail.MessagingException;
import mg.manao.backend.exception.ApiException;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import javax.net.ssl.SSLHandshakeException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EmailServiceTest {

    @Test
    void smtpDiagnosticFindsNestedCausesAndUsesSafeCategories() throws Exception {
        MessagingException smtpFailure = new MessagingException(
                "recipient=ada@example.com reset-code=381624 app-password-sensitive");
        smtpFailure.setNextException(new AuthenticationFailedException(
                "recipient=ada@example.com reset-code=381624 app-password-sensitive"));

        String authentication = EmailService.diagnosticSmtpCause(smtpFailure);
        String connectionRefused = EmailService.diagnosticSmtpCause(
                new ConnectException("private server response"));
        String timeout = EmailService.diagnosticSmtpCause(
                new RuntimeException("private wrapper", new SocketTimeoutException("private timeout")));
        String tls = EmailService.diagnosticSmtpCause(
                new RuntimeException("private wrapper", new SSLHandshakeException("private TLS response")));

        assertTrue(authentication.contains("SMTP authentication rejected"));
        assertTrue(connectionRefused.contains("connection refused"));
        assertTrue(timeout.contains("timeout"));
        assertTrue(tls.contains("TLS/SSL failure"));

        String diagnostics = String.join(" ", authentication, connectionRefused, timeout, tls);
        assertFalse(diagnostics.contains("ada@example.com"));
        assertFalse(diagnostics.contains("381624"));
        assertFalse(diagnostics.contains("private-value"));
        assertFalse(diagnostics.contains("private server response"));
    }

    @Test
    void resetNeverFallsBackToSmtpWhenGmailApiIsDisabled() {
        String recipient = "ada.sensitive@gmail.com";
        JavaMailSender mailSender = mock(JavaMailSender.class);
        GmailApiSender gmailApi = mock(GmailApiSender.class);
        EmailService emailService = new EmailService(mailSender, gmailApi);
        when(gmailApi.isEnabled()).thenReturn(false);

        ApiException error = assertThrows(ApiException.class,
                () -> emailService.envoyerCodeReinitialisation(recipient, "Ada", "927351"));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, error.getStatus());
        verify(mailSender, never()).send(any(SimpleMailMessage.class));
    }

    @Test
    void invalidOAuthConfigurationReturnsControlledServiceUnavailable() {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        GmailApiSender gmailApi = mock(GmailApiSender.class);
        EmailService emailService = new EmailService(mailSender, gmailApi);
        when(gmailApi.isEnabled()).thenReturn(true);
        when(gmailApi.isConfigured()).thenReturn(true);
        when(gmailApi.verifierJeton()).thenReturn(
                new GmailApiSender.Resultat(false, false, "OAuth invalid_grant"));

        ApiException error = assertThrows(ApiException.class,
                emailService::verifierConfigurationReinitialisation);

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, error.getStatus());
        verify(gmailApi, never()).envoyer(any(String.class), any(String.class), any(String.class));
        verify(mailSender, never()).send(any(SimpleMailMessage.class));
    }

    @Test
    void gmailApiFailureDoesNotFallbackToSmtpAndDoesNotLogSecretsOrCode() {
        String recipient = "ada.sensitive@gmail.com";
        String resetCode = "927351";
        String refreshToken = "private-refresh-token";
        JavaMailSender mailSender = mock(JavaMailSender.class);
        GmailApiSender gmailApi = mock(GmailApiSender.class);
        when(gmailApi.isEnabled()).thenReturn(true);
        when(gmailApi.isConfigured()).thenReturn(true);
        when(gmailApi.envoyer(eq(recipient), any(String.class), any(String.class)))
                .thenReturn(new GmailApiSender.Resultat(false, false,
                        "HTTP 403 (permission Gmail refusée)"));
        EmailService emailService = new EmailService(mailSender, gmailApi);

        Logger logger = (Logger) LoggerFactory.getLogger(EmailService.class);
        Level previousLevel = logger.getLevel();
        ListAppender<ch.qos.logback.classic.spi.ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.setLevel(Level.ERROR);
        logger.addAppender(appender);

        try {
            assertFalse(emailService.envoyerCodeReinitialisation(recipient, "Ada", resetCode));
            verify(mailSender, never()).send(any(SimpleMailMessage.class));
            verify(gmailApi).envoyer(eq(recipient), any(String.class), contains(resetCode));

            String logs = appender.list.stream()
                    .map(ch.qos.logback.classic.spi.ILoggingEvent::getFormattedMessage)
                    .reduce("", (left, right) -> left + "\n" + right);
            assertTrue(logs.contains("HTTP 403"));
            assertTrue(logs.contains("a***@gmail.com"));
            assertFalse(logs.contains(recipient));
            assertFalse(logs.contains(resetCode));
            assertFalse(logs.contains(refreshToken));
        } finally {
            logger.detachAppender(appender);
            appender.stop();
            logger.setLevel(previousLevel);
        }
    }
}
