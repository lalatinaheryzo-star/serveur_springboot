package mg.manao.backend.service;

import lombok.RequiredArgsConstructor;
import mg.manao.backend.dto.auth.AuthResponse;
import mg.manao.backend.dto.auth.LoginRequest;
import mg.manao.backend.dto.auth.RegisterRequest;
import mg.manao.backend.entity.Utilisateur;
import mg.manao.backend.exception.ApiException;
import mg.manao.backend.repository.UtilisateurRepository;
import mg.manao.backend.security.JwtService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.OffsetDateTime;

import static mg.manao.backend.service.UtilisateurService.toDto;

@Service
@RequiredArgsConstructor
public class AuthService {

    /** Durée de validité d'un code de réinitialisation (inchangée : 30 min). */
    private static final int CODE_VALIDITE_MINUTES = 30;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UtilisateurRepository utilisateurRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final EmailExistenceValidator emailExistenceValidator;

    /** Code à 6 chiffres renvoyé à l'appelant après un clic sur "Nouveau code". */
    public record NouveauCode(String code, OffsetDateTime expiration) {}

    public AuthResponse login(LoginRequest req) {
        Utilisateur user = utilisateurRepository.findByEmailIgnoreCase(req.getEmail().trim())
                .orElseThrow(() -> ApiException.unauthorized("Email ou mot de passe incorrect."));

        if (!passwordEncoder.matches(req.getPassword(), user.getMotDePasse())) {
            throw ApiException.unauthorized("Email ou mot de passe incorrect.");
        }

        // Plus aucun blocage lié à la vérification de l'adresse e-mail :
        // un compte existant est un compte utilisable (ADMIN, PRESIDENT,
        // VOYAGEUR). Les rôles et le JWT sont inchangés.
        String token = jwtService.generateToken(user);
        return new AuthResponse(token, toDto(user));
    }

    /**
     * Inscription directe : le compte est créé et immédiatement actif.
     * Aucun code de vérification n'est généré ni envoyé par e-mail.
     * Un token JWT est émis dès la création, ce qui permet à
     * IntegrerCooperative (demande Président) d'enchaîner sans repasser
     * par un écran intermédiaire.
     */
    @Transactional
    public AuthResponse register(RegisterRequest req) {
        String email = req.getEmail().trim().toLowerCase();
        if (utilisateurRepository.existsByEmailIgnoreCase(email)) {
            throw ApiException.conflict("Cet email est déjà utilisé.");
        }
        // Contrôle conservé : le domaine doit pouvoir recevoir du courrier
        // (fautes de frappe, domaines inventés). Ce n'est PAS la vérification
        // par code Gmail — aucun e-mail n'est envoyé ici.
        if (!emailExistenceValidator.domaineAcceptantDuCourrier(email)) {
            throw ApiException.badRequest(
                    "Cette adresse e-mail semble invalide : le domaine ne peut pas recevoir de courrier. "
                    + "Vérifiez l'orthographe de votre adresse.");
        }

        Utilisateur user = Utilisateur.builder()
                .nom(req.getNom())
                .prenom(req.getPrenom())
                .email(email)
                .telephone(req.getTelephone())
                .motDePasse(passwordEncoder.encode(req.getPassword()))
                .role(Utilisateur.Role.VOYAGEUR)
                .emailVerifie(true)
                .build();
        user = utilisateurRepository.save(user);

        return new AuthResponse(jwtService.generateToken(user), toDto(user));
    }

    /**
     * Bouton "Nouveau code" — génère un code de réinitialisation à 6
     * chiffres pour le compte identifié par son e-mail.
     *
     * Règles :
     *  - un seul code valide à la fois : le nouveau code écrase l'ancien,
     *    qui devient immédiatement invalide ;
     *  - le code est stocké HACHÉ (BCrypt), jamais en clair ;
     *  - il expire au bout de 30 minutes ;
     *  - le compte ADMIN reste exclu de cette fonctionnalité.
     */
    @Transactional
    public NouveauCode genererNouveauCode(String email) {
        Utilisateur user = utilisateurRepository.findByEmailIgnoreCase(email.trim())
                .orElseThrow(() -> ApiException.notFound("Aucun compte ne correspond à cette adresse e-mail."));

        if (user.getRole() == Utilisateur.Role.ADMIN) {
            throw ApiException.badRequest(
                    "Le compte administrateur ne dispose pas de la récupération de mot de passe.");
        }

        String code = String.format("%06d", RANDOM.nextInt(1_000_000));
        OffsetDateTime expiration = OffsetDateTime.now().plusMinutes(CODE_VALIDITE_MINUTES);

        user.setCodeReinitialisation(passwordEncoder.encode(code));
        user.setCodeReinitialisationExpiration(expiration);
        utilisateurRepository.save(user);

        return new NouveauCode(code, expiration);
    }

    /**
     * Réinitialisation effective : le code fourni doit être le DERNIER code
     * généré, non expiré et non encore utilisé. En cas de succès, le code
     * est effacé — il ne peut donc pas servir une seconde fois.
     */
    @Transactional
    public void reinitialiserMotDePasse(String email, String code, String nouveauMotDePasse) {
        Utilisateur user = utilisateurRepository.findByEmailIgnoreCase(email.trim())
                .orElseThrow(() -> ApiException.badRequest("Code invalide ou expiré."));

        if (user.getRole() == Utilisateur.Role.ADMIN) {
            throw ApiException.badRequest("Code invalide ou expiré.");
        }

        String hash = user.getCodeReinitialisation();
        if (hash == null) {
            throw ApiException.badRequest("Aucun code en cours. Cliquez sur \"Nouveau code\".");
        }
        if (user.getCodeReinitialisationExpiration() == null
                || user.getCodeReinitialisationExpiration().isBefore(OffsetDateTime.now())) {
            throw ApiException.badRequest("Ce code a expiré. Cliquez sur \"Nouveau code\".");
        }
        if (!passwordEncoder.matches(code == null ? "" : code.trim(), hash)) {
            // Un ancien code ne correspond plus au hash stocké : il est donc
            // refusé ici, sans traitement particulier.
            throw ApiException.badRequest("Code incorrect.");
        }
        if (nouveauMotDePasse == null || nouveauMotDePasse.length() < 6) {
            throw ApiException.badRequest("Le mot de passe doit contenir au moins 6 caractères.");
        }

        user.setMotDePasse(passwordEncoder.encode(nouveauMotDePasse));
        user.setCodeReinitialisation(null);          // usage unique
        user.setCodeReinitialisationExpiration(null);
        utilisateurRepository.save(user);
    }
}
