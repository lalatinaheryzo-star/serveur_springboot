package mg.manao.backend.controller;

import lombok.RequiredArgsConstructor;
import mg.manao.backend.service.EmailService;
import mg.manao.backend.service.EmailService.EmailDiagnostic;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Réservé ADMIN (voir SecurityConfig : /diagnostic/** -> hasRole("ADMIN")).
 * Permet de vérifier la configuration SMTP Gmail sans passer par une
 * inscription complète, et sans jamais exposer GMAIL_APP_PASSWORD.
 */
@RestController
@RequestMapping("/diagnostic/email")
@RequiredArgsConstructor
public class EmailDiagnosticController {

    private final EmailService emailService;

    /** GET /api/diagnostic/email/connexion — teste juste la connexion + l'authentification SMTP. */
    @GetMapping("/connexion")
    public ResponseEntity<EmailDiagnostic> testerConnexion() {
        EmailDiagnostic resultat = emailService.diagnostiquerConnexion();
        return ResponseEntity.ok(resultat);
    }

    /** POST /api/diagnostic/email/test?destinataire=toi@example.com — envoie un vrai e-mail de test. */
    @PostMapping("/test")
    public ResponseEntity<EmailDiagnostic> envoyerTest(@RequestParam String destinataire) {
        EmailDiagnostic resultat = emailService.envoyerEmailTest(destinataire);
        return ResponseEntity.ok(resultat);
    }
}
