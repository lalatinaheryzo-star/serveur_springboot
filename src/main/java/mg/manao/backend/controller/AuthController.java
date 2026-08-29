package mg.manao.backend.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import mg.manao.backend.dto.UtilisateurDTO;
import mg.manao.backend.dto.auth.AuthResponse;
import mg.manao.backend.dto.auth.LoginRequest;
import mg.manao.backend.dto.auth.RegisterRequest;
import mg.manao.backend.exception.ApiException;
import mg.manao.backend.security.CurrentUser;
import mg.manao.backend.service.AuthService;
import mg.manao.backend.service.EmailVerificationService;
import mg.manao.backend.service.UtilisateurService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final UtilisateurService utilisateurService;
    private final EmailVerificationService emailVerificationService;

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest req) {
        return ResponseEntity.ok(authService.login(req));
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest req) {
        return ResponseEntity.status(201).body(authService.register(req));
    }

    /** Lien cliqué depuis l'e-mail de vérification (ou code collé manuellement côté frontend). */
    @PostMapping("/verify-email")
    public ResponseEntity<Void> verifyEmail(@Valid @RequestBody VerifyEmailRequest req) {
        emailVerificationService.verifier(req.getToken());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/resend-verification")
    public ResponseEntity<Void> resendVerification(@Valid @RequestBody ResendVerificationRequest req) {
        emailVerificationService.renvoyer(req.getEmail());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public ResponseEntity<UtilisateurDTO> me() {
        var current = CurrentUser.get();
        if (current == null) throw ApiException.unauthorized("Non authentifié.");
        return ResponseEntity.ok(utilisateurService.findById(current.getId()));
    }

    @Getter @Setter
    public static class VerifyEmailRequest {
        @NotBlank
        private String token;
    }

    @Getter @Setter
    public static class ResendVerificationRequest {
        @NotBlank @Email
        private String email;
    }
}
