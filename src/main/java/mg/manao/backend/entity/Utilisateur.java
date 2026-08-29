package mg.manao.backend.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "utilisateurs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Utilisateur {

    public enum Role { ADMIN, PRESIDENT, VOYAGEUR }

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false)
    private String nom;

    @Column(nullable = false)
    private String prenom;

    @Column(nullable = false, unique = true)
    private String email;

    private String telephone;

    // Hash BCrypt — jamais exposé dans les DTO.
    @Column(name = "mot_de_passe", nullable = false)
    private String motDePasse;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private Role role = Role.VOYAGEUR;

    @Column(name = "date_creation", nullable = false, updatable = false)
    private OffsetDateTime dateCreation;

    // ── Vérification réelle de l'adresse e-mail (double opt-in) ──────────
    // Un compte VOYAGEUR (et donc, par promotion, un futur PRESIDENT) créé
    // via /auth/register ne peut pas se connecter tant que emailVerifie
    // n'est pas passé à true (voir AuthService.register()/login() et
    // EmailVerificationService). Le compte ADMIN, provisionné une seule
    // fois via DataSeeder à partir d'une adresse déjà connue et réelle,
    // est marqué vérifié dès sa création.
    @Column(name = "email_verifie", nullable = false)
    @Builder.Default
    private boolean emailVerifie = false;

    @Column(name = "code_verification")
    private String codeVerification;

    @Column(name = "code_verification_expiration")
    private OffsetDateTime codeVerificationExpiration;

    @PrePersist
    public void prePersist() {
        if (dateCreation == null) dateCreation = OffsetDateTime.now();
    }
}
