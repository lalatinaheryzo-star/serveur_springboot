package mg.manao.backend.service;

import lombok.RequiredArgsConstructor;
import mg.manao.backend.entity.Utilisateur;
import mg.manao.backend.exception.ApiException;
import mg.manao.backend.repository.UtilisateurRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Double opt-in : preuve qu'une adresse e-mail syntaxiquement valide et sur
 * un domaine existant (voir EmailExistenceValidator) est bien accessible par
 * la personne qui s'inscrit. Tant que le compte n'est pas vérifié, la
 * connexion est refusée (voir AuthService.login()).
 */
@Service
@RequiredArgsConstructor
public class EmailVerificationService {

    private static final int VALIDITE_MINUTES = 30;

    private final UtilisateurRepository utilisateurRepository;
    private final EmailService emailService;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    /** Génère un nouveau code, l'enregistre sur le compte, et tente l'envoi. */
    @Transactional
    public void envoyerVerification(Utilisateur user) {
        String token = UUID.randomUUID().toString().replace("-", "");
        user.setCodeVerification(token);
        user.setCodeVerificationExpiration(OffsetDateTime.now().plusMinutes(VALIDITE_MINUTES));
        utilisateurRepository.save(user);

        String lien = frontendUrl + "/verifier-email?token=" + token;
        boolean envoye = emailService.envoyerCodeVerification(user.getEmail(), user.getPrenom(), token, lien);
        if (!envoye) {
            // On ne bloque pas la création du compte pour autant (SMTP peut
            // être temporairement indisponible), mais l'utilisateur doit le
            // savoir immédiatement plutôt que d'attendre en vain un e-mail.
            throw ApiException.badRequest(
                    "Votre compte a été créé, mais l'e-mail de vérification n'a pas pu être envoyé pour le moment. "
                    + "Réessayez depuis \"Renvoyer le code\" dans quelques instants.");
        }
    }

    @Transactional
    public void verifier(String token) {
        Utilisateur user = utilisateurRepository.findByCodeVerification(token)
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
