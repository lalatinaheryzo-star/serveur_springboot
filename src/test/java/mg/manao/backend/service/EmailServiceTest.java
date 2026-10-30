package mg.manao.backend.service;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.core.read.ListAppender;
import jakarta.mail.AuthenticationFailedException;
import jakarta.mail.MessagingException;
import org.junit.jupiter.api.Test;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.test.util.ReflectionTestUtils;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLHandshakeException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

class EmailServiceTest {

    @Test
    void smtpDiagnosticFindsNestedCausesAndUsesSafeCategories() throws Exception {
        MessagingException smtpFailure = new MessagingException(
                "recipient=ada@example.com reset-code=381624 app-password=private-value");
        smtpFailure.setNextException(new AuthenticationFailedException(
                "recipient=ada@example.com reset-code=381624 app-password=private-value"));

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
    void smtpFailureLogsOnlyMaskedAddressAndSanitizedCause() {
        String recipient = "ada.sensitive@gmail.com";
        String resetCode = "927351";
        String appPassword = "app-password-sensitive";
        String exceptionMessage = "recipient=" + recipient
                + " reset-code=" + resetCode
                + " app-password=" + appPassword
                + " email-body=private-content";
        MessagingException smtpFailure = new MessagingException(exceptionMessage);
        smtpFailure.setNextException(new AuthenticationFailedException(exceptionMessage));

        JavaMailSender mailSender = mock(JavaMailSender.class);
        doThrow(new MailSendException(exceptionMessage, smtpFailure))
                .when(mailSender).send(any(SimpleMailMessage.class));

        EmailService emailService = new EmailService(mailSender);
        ReflectionTestUtils.setField(emailService, "enabled", true);
        ReflectionTestUtils.setField(emailService, "username", "sender@gmail.com");
        ReflectionTestUtils.setField(emailService, "password", appPassword);
        ReflectionTestUtils.setField(emailService, "from", "sender@gmail.com");

        Logger logger = (Logger) LoggerFactory.getLogger(EmailService.class);
        Level previousLevel = logger.getLevel();
        ListAppender<ch.qos.logback.classic.spi.ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.setLevel(Level.WARN);
        logger.addAppender(appender);

        try {
            assertFalse(emailService.envoyerCodeReinitialisation(recipient, "Ada", resetCode));

            String logs = appender.list.stream()
                    .map(ch.qos.logback.classic.spi.ILoggingEvent::getFormattedMessage)
                    .reduce("", (left, right) -> left + "\n" + right);

            assertTrue(logs.contains("a***@gmail.com"));
            assertTrue(logs.contains("SMTP authentication rejected"));
            assertFalse(logs.contains(recipient));
            assertFalse(logs.contains(resetCode));
            assertFalse(logs.contains(appPassword));
            assertFalse(logs.contains("private-content"));
        } finally {
            logger.detachAppender(appender);
            appender.stop();
            logger.setLevel(previousLevel);
        }
    }
}
