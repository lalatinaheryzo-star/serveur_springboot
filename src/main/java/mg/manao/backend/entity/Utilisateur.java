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

    // ── Statut du compte ────────────────────────────────────────────────
    // Colonne conservée (aucune suppression en base) mais elle ne bloque
    // plus la connexion : depuis la suppression de la vérification Gmail à
    // l'inscription, tout compte créé via /auth/register est actif
    // immédiatement et ce champ vaut true dès la création.
    @Column(name = "email_verifie", nullable = false)
    @Builder.Default
    private boolean emailVerifie = true;

    // ── Code de réinitialisation du mot de passe ("Nouveau code") ────────
    // Ces deux colonnes existaient déjà (code_verification /
    // code_verification_expiration) pour la vérification d'inscription :
    // elles sont RÉUTILISÉES telles quelles pour le code "Mot de passe
    // oublié", donc aucune migration de base n'est nécessaire.
    // Le code n'est jamais stocké en clair : on garde son hash BCrypt.
    // Un seul code à la fois par compte -> générer un nouveau code écrase
    // l'ancien, qui devient de fait invalide.
    @Column(name = "code_verification")
    private String codeReinitialisation;

    @Column(name = "code_verification_expiration")
    private OffsetDateTime codeReinitialisationExpiration;

    // ── Limitation des tentatives (voir AuthService) ─────────────────────
    // Compteurs persistés en base (et non en mémoire) pour rester corrects
    // avec plusieurs instances du backend. Nécessitent la migration
    // database_migration_auth_rate_limit.sql (ddl-auto=validate).

    /** Tentatives incorrectes sur le code de réinitialisation en cours. */
    @Column(name = "reset_tentatives", nullable = false)
    @Builder.Default
    private int resetTentatives = 0;

    /** Dernière demande de code de réinitialisation traitée (délai minimal entre deux demandes). */
    @Column(name = "reset_derniere_demande")
    private OffsetDateTime resetDerniereDemande;

    /** Échecs de connexion consécutifs. */
    @Column(name = "login_echecs", nullable = false)
    @Builder.Default
    private int loginEchecs = 0;

    /** Connexion refusée tant que cette date n'est pas dépassée. */
    @Column(name = "login_verrou_jusqua")
    private OffsetDateTime loginVerrouJusqua;

    /**
     * Date du dernier changement de mot de passe (réinitialisation ou modification par l'admin).
     * Les JWT émis AVANT cette date sont refusés (voir JwtAuthFilter). Null = jamais changé :
     * tous les jetons restent valides. Migration : database_migration_jwt_invalidation.sql.
     */
    @Column(name = "mot_de_passe_modifie_le")
    private OffsetDateTime motDePasseModifieLe;

    @PrePersist
    public void prePersist() {
        if (dateCreation == null) dateCreation = OffsetDateTime.now();
    }
}
