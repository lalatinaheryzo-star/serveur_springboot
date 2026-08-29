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

import static mg.manao.backend.service.UtilisateurService.toDto;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UtilisateurRepository utilisateurRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final EmailExistenceValidator emailExistenceValidator;
    private final EmailVerificationService emailVerificationService;

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
}
