package mg.manao.backend.service;

import lombok.RequiredArgsConstructor;
import mg.manao.backend.entity.Utilisateur;
import mg.manao.backend.exception.ApiException;
import mg.manao.backend.repository.UtilisateurRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.OffsetDateTime;

/**
 * Double opt-in : preuve qu'une adresse e-mail syntaxiquement valide et sur
 * un domaine existant (voir EmailExistenceValidator) est bien accessible par
 * la personne qui s'inscrit. Tant que le compte n'est pas vérifié, la
 * connexion est refusée (voir AuthService.login()).
 *
 * Code à 6 chiffres (et non plus un token hexadécimal de 32 caractères) :
 * un identifiant aussi long, envoyé en texte, se fait facilement couper en
 * deux lignes par certains clients mail — ce qui cassait le lien ou
 * tronquait le code au copier-coller, d'où le fonctionnement intermittent.
 * 6 chiffres tiennent toujours sur une ligne et se recopient sans erreur.
 */
@Service
@RequiredArgsConstructor
public class EmailVerificationService {

    private static final int VALIDITE_MINUTES = 30;
    private static final int TENTATIVES_UNICITE_MAX = 20;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UtilisateurRepository utilisateurRepository;
    private final EmailService emailService;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    /** Génère un nouveau code à 6 chiffres, l'enregistre sur le compte, et tente l'envoi. */
    @Transactional
    public void envoyerVerification(Utilisateur user) {
        String code = genererCodeUnique();
        user.setCodeVerification(code);
        user.setCodeVerificationExpiration(OffsetDateTime.now().plusMinutes(VALIDITE_MINUTES));
        utilisateurRepository.save(user);

        String lien = frontendUrl + "/verifier-email?token=" + code;
        boolean envoye = emailService.envoyerCodeVerification(user.getEmail(), user.getPrenom(), code, lien);
        if (!envoye) {
            // On ne bloque pas la création du compte pour autant (SMTP peut
            // être temporairement indisponible), mais l'utilisateur doit le
            // savoir immédiatement plutôt que d'attendre en vain un e-mail.
            throw ApiException.badRequest(
                    "Votre compte a été créé, mais l'e-mail de vérification n'a pas pu être envoyé pour le moment. "
                    + "Réessayez depuis \"Renvoyer le code\" dans quelques instants.");
        }
    }

    /**
     * Un code à 6 chiffres n'offre "que" 1 000 000 de combinaisons : on
     * vérifie donc qu'aucun autre compte en attente de vérification n'a déjà
     * le même code avant de l'attribuer, pour exclure toute ambiguïté au
     * moment de verifier() ci-dessous (en pratique la collision n'arrive
     * presque jamais, mais autant l'empêcher plutôt que la subir).
     */
    private String genererCodeUnique() {
        for (int tentative = 0; tentative < TENTATIVES_UNICITE_MAX; tentative++) {
            String code = String.format("%06d", RANDOM.nextInt(1_000_000));
            if (utilisateurRepository.findByCodeVerification(code).isEmpty()) {
                return code;
            }
        }
        return String.format("%06d", RANDOM.nextInt(1_000_000));
    }

    @Transactional
    public void verifier(String token) {
        Utilisateur user = utilisateurRepository.findByCodeVerification(token == null ? null : token.trim())
                .orElseThrow(() -> ApiException.badRequest("Code de vérification invalide ou déjà utilisé."));

        if (user.getCodeVerificationExpiration() == null
                || user.getCodeVerificationExpiration().isBefore(OffsetDateTime.now())) {
            throw ApiException.badRequest("Ce code de vérification a expiré. Demandez-en un nouveau.");
        }

        user.setEmailVerifie(true);
        user.setCodeVerification(null);
        user.setCodeVerificationExpiration(null);
        utilisateurRepository.save(user);
    }

    @Transactional
    public void renvoyer(String email) {
        Utilisateur user = utilisateurRepository.findByEmailIgnoreCase(email.trim())
                .orElseThrow(() -> ApiException.notFound("Aucun compte ne correspond à cet e-mail."));
        if (user.isEmailVerifie()) {
            throw ApiException.conflict("Cette adresse e-mail est déjà vérifiée.");
        }
        envoyerVerification(user);
    }
}
