package mg.manao.backend.service;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import org.springframework.transaction.support.TransactionTemplate;

import java.security.SecureRandom;
import java.time.OffsetDateTime;

import static mg.manao.backend.service.UtilisateurService.toDto;

@Service
@RequiredArgsConstructor
public class AuthService {

    /** Durée de validité d'un code de réinitialisation (inchangée : 30 min). */
    static final int CODE_VALIDITE_MINUTES = 30;
    /** Tentatives incorrectes tolérées sur un même code ; au-delà, le code est invalidé. */
    static final int MAX_TENTATIVES_CODE = 5;
    /** Délai minimal entre deux demandes de code pour un même compte. */
    static final int DELAI_MIN_ENTRE_DEMANDES_SECONDES = 60;
    /** Échecs de connexion consécutifs avant verrouillage temporaire du compte. */
    static final int MAX_ECHECS_CONNEXION = 5;
    /** Durée maximale du verrouillage. Le verrou est PROGRESSIF : 1 min, puis 4, puis 15 (plafond). */
    static final int VERROU_CONNEXION_MINUTES = 15;

    /**
     * Durée du verrou selon le nombre d'échecs cumulés (jamais remis à zéro tant que l'utilisateur
     * légitime ne s'est pas connecté ou n'a pas réinitialisé son mot de passe). Un tiers qui
     * s'acharne obtient des verrous de plus en plus longs, mais une rafale isolée de 5 essais ne
     * bloque le vrai propriétaire qu'une minute (limite l'abus de verrouillage d'un compte tiers).
     */
    static int dureeVerrouMinutes(int echecsCumules) {
        int palier = Math.max(0, (echecsCumules - MAX_ECHECS_CONNEXION) / MAX_ECHECS_CONNEXION);
        return switch (palier) {
            case 0 -> 1;
            case 1 -> 4;
            default -> VERROU_CONNEXION_MINUTES;
        };
    }

    static final String MESSAGE_CONNEXION_ECHEC = "Email ou mot de passe incorrect.";
    static final String MESSAGE_CODE_INVALIDE = "Code invalide ou expiré.";

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UtilisateurRepository utilisateurRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final EmailExistenceValidator emailExistenceValidator;
    private final EmailService emailService;
    private final TransactionTemplate transactionTemplate;

    /** Hash factice, calculé une seule fois, pour égaliser le temps de réponse quand le compte n'existe pas. */
    private volatile String hashFactice;

    private String hashFactice() {
        String h = hashFactice;
        if (h == null) {
            h = passwordEncoder.encode("mot-de-passe-factice-jamais-utilise");
            hashFactice = h;
        }
        return h;
    }

    /** Effectue un calcul BCrypt « pour rien » afin que « compte inconnu » ne réponde pas nettement plus vite. */
    private void travailFactice(String secret) {
        passwordEncoder.matches(secret == null ? "" : secret, hashFactice());
    }

    /**
     * Connexion avec limitation des échecs (AUTH-03).
     * Le compte est verrouillé en lecture/écriture pendant la vérification : des requêtes
     * parallèles sur le même compte sont donc comptées l'une après l'autre.
     * Les réponses d'échec sont identiques (401, même message) pour : compte inconnu,
     * mauvais mot de passe et compte temporairement verrouillé (AUTH-07).
     * noRollbackFor : le compteur d'échecs doit être conservé même quand on lève l'exception.
     */
    @Transactional(noRollbackFor = ApiException.class)
    public AuthResponse login(LoginRequest req) {
        String email = req.getEmail() == null ? "" : req.getEmail().trim();
        Utilisateur user = utilisateurRepository.findByEmailForUpdate(email).orElse(null);

        if (user == null) {
            travailFactice(req.getPassword());
            throw ApiException.unauthorized(MESSAGE_CONNEXION_ECHEC);
        }

        OffsetDateTime maintenant = OffsetDateTime.now();
        boolean verrouille = user.getLoginVerrouJusqua() != null
                && user.getLoginVerrouJusqua().isAfter(maintenant);
        boolean motDePasseOk = passwordEncoder.matches(
                req.getPassword() == null ? "" : req.getPassword(), user.getMotDePasse());

        if (verrouille) {
            // Même un bon mot de passe est refusé pendant le verrouillage.
            throw ApiException.unauthorized(MESSAGE_CONNEXION_ECHEC);
        }

        if (!motDePasseOk) {
            int echecs = user.getLoginEchecs() + 1;
            user.setLoginEchecs(echecs);
            // Verrou dès 5 échecs ; chaque série supplémentaire de 5 échecs allonge la durée.
            if (echecs >= MAX_ECHECS_CONNEXION && echecs % MAX_ECHECS_CONNEXION == 0) {
                int minutes = dureeVerrouMinutes(echecs);
                user.setLoginVerrouJusqua(maintenant.plusMinutes(minutes));
                log.warn("Compte {} verrouillé {} min après {} échecs de connexion.",
                        user.getId(), minutes, echecs);
            }
            utilisateurRepository.save(user);
            throw ApiException.unauthorized(MESSAGE_CONNEXION_ECHEC);
        }

        if (user.getLoginEchecs() != 0 || user.getLoginVerrouJusqua() != null) {
            user.setLoginEchecs(0);
            user.setLoginVerrouJusqua(null);
            utilisateurRepository.save(user);
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
     * Le rôle est toujours VOYAGEUR : une demande de coopérative ne donne
     * aucun droit de Président avant validation par l'Admin.
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
     * Ancien point d'entrée « mot de passe oublié » (clients mobiles) : il ne change PLUS
     * le mot de passe et n'envoie JAMAIS de mot de passe. Il redirige vers le parcours
     * unique par code temporaire (genererNouveauCode).
     */
    public void forgotPassword(String email) {
        genererNouveauCode(email);
    }

    /**
     * Demande de code de réinitialisation (bouton « Nouveau code »).
     *
     * Trois étapes, pour ne JAMAIS garder de connexion base ni de verrou pendant l'envoi SMTP
     * (sinon quelques demandes parallèles sur un même compte épuiseraient le pool de 5 connexions) :
     *  1. transaction courte avec verrou : contrôle du délai minimal et réservation de la demande ;
     *  2. envoi de l'e-mail, hors transaction ;
     *  3. transaction courte avec verrou : enregistrement du hash du nouveau code, seulement si l'envoi a réussi.
     *
     * - Deux demandes simultanées : la seconde attend le verrou de l'étape 1, voit le délai déjà
     *   consommé et est ignorée -> un seul e-mail.
     * - Échec d'envoi : le code précédent, encore valable, est conservé (AUTH-06).
     * - Le délai est consommé même si l'envoi échoue, pour ne pas marteler le SMTP.
     * - Réponse externe identique dans tous les cas (AUTH-07).
     * Limite connue : le temps de réponse d'un compte existant (envoi SMTP synchrone) reste
     * nettement plus long que celui d'un compte inconnu.
     */
    public void genererNouveauCode(String email) {
        String adresse = email == null ? "" : email.trim();

        DemandeCode demande = transactionTemplate.execute(status -> reserverDemandeDeCode(adresse));
        if (demande == null) return;

        String code = String.format("%06d", RANDOM.nextInt(1_000_000));
        boolean envoye;
        try {
            envoye = emailService.envoyerCodeReinitialisation(demande.email(), demande.prenom(), code);
        } catch (RuntimeException e) {
            envoye = false;
        }
        if (!envoye) {
            log.warn("Code de réinitialisation non envoyé (compte {}) : l'éventuel code précédent est conservé.",
                    demande.id());
            return;
        }

        String hash = passwordEncoder.encode(code);
        transactionTemplate.executeWithoutResult(status ->
                utilisateurRepository.findByEmailForUpdate(demande.email()).ifPresent(u -> {
                    u.setCodeReinitialisation(hash);
                    u.setCodeReinitialisationExpiration(OffsetDateTime.now().plusMinutes(CODE_VALIDITE_MINUTES));
                    u.setResetTentatives(0);
                    utilisateurRepository.save(u);
                }));
    }

    private record DemandeCode(java.util.UUID id, String email, String prenom) {}

    /** Étape 1 : renvoie null (aucun envoi) pour compte inconnu, ADMIN ou demande trop rapprochée. */
    private DemandeCode reserverDemandeDeCode(String adresse) {
        Utilisateur user = utilisateurRepository.findByEmailForUpdate(adresse).orElse(null);
        if (user == null || user.getRole() == Utilisateur.Role.ADMIN) {
            // Même type de calcul BCrypt que pour un compte réel (le hash du code).
            passwordEncoder.encode(String.format("%06d", RANDOM.nextInt(1_000_000)));
            return null;
        }
        OffsetDateTime maintenant = OffsetDateTime.now();
        OffsetDateTime derniere = user.getResetDerniereDemande();
        if (derniere != null && derniere.plusSeconds(DELAI_MIN_ENTRE_DEMANDES_SECONDES).isAfter(maintenant)) {
            return null;
        }
        user.setResetDerniereDemande(maintenant);
        utilisateurRepository.save(user);
        return new DemandeCode(user.getId(), user.getEmail(), user.getPrenom());
    }

    /**
     * Réinitialisation effective : le code fourni doit être le DERNIER code généré,
     * non expiré, non encore utilisé, et le plafond d'essais ne doit pas être atteint.
     *
     * Toutes les causes d'échec (compte inconnu, ADMIN, aucun code, expiré, plafond atteint,
     * mauvais code) renvoient la même réponse : 400 « Code invalide ou expiré. » (AUTH-07).
     * Chaque mauvais code incrémente un compteur persisté ; au {@value #MAX_TENTATIVES_CODE}e échec,
     * le code est invalidé et une nouvelle demande est nécessaire (AUTH-03/AUTH-06).
     * En cas de succès le code est effacé : usage unique.
     * noRollbackFor : les compteurs d'échecs doivent survivre à l'exception levée.
     */
    @Transactional(noRollbackFor = ApiException.class)
    public void reinitialiserMotDePasse(String email, String code, String nouveauMotDePasse) {
        // Contrôle indépendant du compte : ne consomme aucun essai.
        if (nouveauMotDePasse == null || nouveauMotDePasse.length() < 6) {
            throw ApiException.badRequest("Le mot de passe doit contenir au moins 6 caractères.");
        }

        String adresse = email == null ? "" : email.trim();
        String codeSaisi = code == null ? "" : code.trim();

        Utilisateur user = utilisateurRepository.findByEmailForUpdate(adresse).orElse(null);
        if (user == null || user.getRole() == Utilisateur.Role.ADMIN) {
            travailFactice(codeSaisi);
            throw ApiException.badRequest(MESSAGE_CODE_INVALIDE);
        }

        OffsetDateTime maintenant = OffsetDateTime.now();
        String hash = user.getCodeReinitialisation();
        OffsetDateTime expiration = user.getCodeReinitialisationExpiration();

        if (hash == null || expiration == null) {
            travailFactice(codeSaisi);
            throw ApiException.badRequest(MESSAGE_CODE_INVALIDE);
        }
        if (expiration.isBefore(maintenant) || user.getResetTentatives() >= MAX_TENTATIVES_CODE) {
            invaliderCode(user);
            utilisateurRepository.save(user);
            travailFactice(codeSaisi);
            throw ApiException.badRequest(MESSAGE_CODE_INVALIDE);
        }

        if (!passwordEncoder.matches(codeSaisi, hash)) {
            int essais = user.getResetTentatives() + 1;
            if (essais >= MAX_TENTATIVES_CODE) {
                invaliderCode(user);
                log.warn("Code de réinitialisation invalidé après {} essais incorrects (compte {}).",
                        MAX_TENTATIVES_CODE, user.getId());
            } else {
                user.setResetTentatives(essais);
            }
            utilisateurRepository.save(user);
            throw ApiException.badRequest(MESSAGE_CODE_INVALIDE);
        }

        user.setMotDePasse(passwordEncoder.encode(nouveauMotDePasse));
        user.setMotDePasseModifieLe(OffsetDateTime.now()); // invalide les JWT déjà émis
        invaliderCode(user); // usage unique
        // Prouver l'accès à la boîte e-mail lève aussi un éventuel verrouillage de connexion.
        user.setLoginEchecs(0);
        user.setLoginVerrouJusqua(null);
        utilisateurRepository.save(user);
    }

    private void invaliderCode(Utilisateur user) {
        user.setCodeReinitialisation(null);
        user.setCodeReinitialisationExpiration(null);
        user.setResetTentatives(0);
    }
}
