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
@Table(name = "cooperatives")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Cooperative {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false)
    private String nom;

    private String adresse;

    private String telephone;

    // Un Président gère au plus une seule coopérative, et une coopérative a
    // au plus un seul Président (contrainte UNIQUE sur president_id en base,
    // voir database/migration_president.sql). Nullable : une coopérative
    // peut exister brièvement sans président (juste après création par un
    // ADMIN, avant que sa demande ne soit rattachée — cas de bord).
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "president_id", unique = true)
    private Utilisateur president;

    @Column(name = "date_creation", nullable = false, updatable = false)
    private OffsetDateTime dateCreation;

    // ── Rappel de voyage par WhatsApp (configurable par coopérative) ──
    // Étape 1 : uniquement la configuration. L'envoi réel (étape 2) lira
    // ces deux champs pour chaque coopérative au moment de déclencher le rappel.
    @Builder.Default
    @Column(name = "rappel_actif", nullable = false)
    private boolean rappelActif = false;

    @Builder.Default
    @Column(name = "rappel_delai_minutes", nullable = false)
    private int rappelDelaiMinutes = 30;

    @PrePersist
    public void prePersist() {
        if (dateCreation == null) dateCreation = OffsetDateTime.now();
    }
}

