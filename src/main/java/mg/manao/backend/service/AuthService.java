package mg.manao.backend.service;

import lombok.RequiredArgsConstructor;
import mg.manao.backend.dto.auth.AuthResponse;
import mg.manao.backend.dto.auth.LoginRequest;
import mg.manao.backend.dto.auth.RegisterRequest;
import mg.manao.backend.entity.Utilisateur;
import mg.manao.backend.exception.ApiException;
import mg.manao.backend.repository.UtilisateurRepository;
import mg.manao.backend.security.JwtService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;

import static mg.manao.backend.service.UtilisateurService.toDto;

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final String MOT_DE_PASSE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UtilisateurRepository utilisateurRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final EmailExistenceValidator emailExistenceValidator;
    private final EmailVerificationService emailVerificationService;
    private final EmailService emailService;

    public AuthResponse login(LoginRequest req) {
        Utilisateur user = utilisateurRepository.findByEmailIgnoreCase(req.getEmail().trim())
                .orElseThrow(() -> ApiException.unauthorized("Email ou mot de passe incorrect."));

        if (!passwordEncoder.matches(req.getPassword(), user.getMotDePasse())) {
            throw ApiException.unauthorized("Email ou mot de passe incorrect.");
        }

        // Un compte créé via /auth/register (VOYAGEUR, et par promotion un
        // futur PRESIDENT) doit avoir confirmé son adresse e-mail avant de
        // pouvoir se connecter. Le compte ADMIN, provisionné par DataSeeder,
        // est toujours déjà marqué vérifié.
        if (!user.isEmailVerifie()) {
            throw ApiException.unauthorized(
                    "Veuillez d'abord vérifier votre adresse e-mail. Consultez votre boîte de réception "
                    + "(et vos spams), ou demandez un nouveau code de vérification.");
        }

        String token = jwtService.generateToken(user);
        return new AuthResponse(token, toDto(user));
    }

    @Transactional
    public AuthResponse register(RegisterRequest req) {
        String email = req.getEmail().trim().toLowerCase();
        if (utilisateurRepository.existsByEmailIgnoreCase(email)) {
            throw ApiException.conflict("Cet email est déjà utilisé.");
        }
        // Étape 1 : le domaine doit réellement pouvoir recevoir du courrier
        // (élimine les domaines inventés/mal orthographiés). Étape 2 (la
        // vraie preuve que CETTE boîte est accessible) : le code de
        // vérification envoyé juste après la création du compte.
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
                .emailVerifie(false)
                .build();
        user = utilisateurRepository.save(user);

        emailVerificationService.envoyerVerification(user);

        // Pas de token JWT tant que l'e-mail n'est pas vérifié : le compte
        // existe, mais l'utilisateur ne peut pas encore l'utiliser (voir
        // login() ci-dessus). Le frontend affiche l'écran "vérifiez votre
        // boîte" à la place d'une connexion automatique.
        return new AuthResponse(null, toDto(user));
    }

    /**
     * "Mot de passe oublié" — Président et Voyageur uniquement, jamais ADMIN
     * (le compte admin n'a pas cette fonctionnalité : il est provisionné une
     * seule fois par AdminSeeder, pas par auto-inscription).
     *
     * Toujours silencieux côté réponse HTTP (voir AuthController) : que
     * l'adresse existe ou non, ou qu'elle appartienne à l'ADMIN, l'appelant
     * ne doit pas pouvoir le déduire de la réponse — seul le contenu de la
     * boîte mail (ou son absence) le révèle à la personne qui la possède
     * réellement.
     */
    @Transactional
    public void forgotPassword(String email) {
        Utilisateur user = utilisateurRepository.findByEmailIgnoreCase(email.trim()).orElse(null);
        if (user == null || user.getRole() == Utilisateur.Role.ADMIN) {
            return;
        }

        String nouveauMotDePasse = genererMotDePasse();
        user.setMotDePasse(passwordEncoder.encode(nouveauMotDePasse));
        utilisateurRepository.save(user);

        boolean envoye = emailService.envoyerNouveauMotDePasse(user.getEmail(), user.getPrenom(), nouveauMotDePasse);
        if (!envoye) {
            // Le mot de passe est déjà changé en base à ce stade : on ne peut
            // pas revenir en arrière sans laisser une fenêtre où l'ancien ET
            // le nouveau mot de passe coexisteraient dans la nature (e-mail
            // parti mais pas confirmé, par ex.). On journalise pour
            // intervention manuelle plutôt que de risquer un compte bloqué.
            log.error("Nouveau mot de passe généré pour {} mais l'e-mail n'a pas pu être envoyé.", user.getEmail());
        }
    }

    private String genererMotDePasse() {
        StringBuilder sb = new StringBuilder(10);
        for (int i = 0; i < 10; i++) {
            sb.append(MOT_DE_PASSE_ALPHABET.charAt(RANDOM.nextInt(MOT_DE_PASSE_ALPHABET.length())));
        }
        return sb.toString();
    }
}
