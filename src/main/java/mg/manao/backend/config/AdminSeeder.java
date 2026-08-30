package mg.manao.backend.config;

import lombok.RequiredArgsConstructor;
import mg.manao.backend.entity.Utilisateur;
import mg.manao.backend.repository.UtilisateurRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Provisionne le compte ADMIN à partir de SEED_ADMIN_EMAIL / SEED_ADMIN_PASSWORD
 * (voir backend/.env) au premier démarrage — uniquement si aucun compte ADMIN
 * n'existe encore. Ne crée plus aucune donnée de démonstration (coopérative,
 * voyage, voyageur, candidat...) : le projet est prêt pour la production, un
 * seul compte réel est amorcé, tout le reste se crée via l'application elle-même.
 */
@Component
@RequiredArgsConstructor
public class AdminSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminSeeder.class);

    private final UtilisateurRepository utilisateurRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.seed.enabled}")
    private boolean seedEnabled;

    @Value("${app.seed.admin-email}")
    private String adminEmail;

    @Value("${app.seed.admin-password}")
    private String adminPassword;

    @Override
    @Transactional
    public void run(String... args) {
        if (!seedEnabled) return;
        if (utilisateurRepository.findByEmailIgnoreCase(adminEmail).isPresent()) return; // déjà provisionné

        utilisateurRepository.save(Utilisateur.builder()
                .nom("Admin").prenom("Plateforme")
                .email(adminEmail)
                .telephone("0340000000")
                .motDePasse(passwordEncoder.encode(adminPassword))
                .role(Utilisateur.Role.ADMIN)
                // Adresse fournie directement par l'exploitant de la plateforme
                // (pas d'auto-inscription publique pour ADMIN) : pas besoin du
                // double opt-in appliqué à /auth/register.
                .emailVerifie(true)
                .build());

        log.info("Compte ADMIN provisionné ({}). Le mot de passe vient de SEED_ADMIN_PASSWORD dans backend/.env "
                + "— ne le committez jamais et changez-le si ce fichier a pu fuiter.", adminEmail);
    }
}
