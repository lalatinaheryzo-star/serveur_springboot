package mg.manao.backend.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import mg.manao.backend.dto.UtilisateurDTO;
import mg.manao.backend.dto.auth.AuthResponse;
import mg.manao.backend.dto.auth.LoginRequest;
import mg.manao.backend.dto.auth.RegisterRequest;
import mg.manao.backend.exception.ApiException;
import mg.manao.backend.security.CurrentUser;
import mg.manao.backend.service.AuthService;
import mg.manao.backend.service.UtilisateurService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final UtilisateurService utilisateurService;

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest req) {
        return ResponseEntity.ok(authService.login(req));
    }

    /**
     * Inscription Voyageur (et point de départ d'une demande Président) :
     * le compte est créé et actif immédiatement, aucun code de vérification
     * n'est envoyé.
     */
    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest req) {
        return ResponseEntity.status(201).body(authService.register(req));
    }

    // ── Mot de passe oublié : MIGRÉ vers le serveur Node.js (server/index.js) ──
    // Les endpoints /auth/forgot-password, /auth/password/nouveau-code et
    // /auth/password/reinitialiser sont désormais gérés par le serveur Express
    // (port 4001 par défaut). Le frontend appelle directement ce serveur.

    @GetMapping("/me")
    public ResponseEntity<UtilisateurDTO> me() {
        var current = CurrentUser.get();
        if (current == null) throw ApiException.unauthorized("Non authentifié.");
        return ResponseEntity.ok(utilisateurService.findById(current.getId()));
    }
}
