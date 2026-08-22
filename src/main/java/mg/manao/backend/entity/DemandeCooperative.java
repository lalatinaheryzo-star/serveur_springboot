package mg.manao.backend.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Demande de création de coopérative (§2 de la spec "Évolution du projet").
 * Un utilisateur (VOYAGEUR) candidat au rôle de Président soumet cette
 * demande ; un ADMIN l'approuve ou la rejette (voir DemandeCooperativeService).
 */
@Entity
@Table(name = "demandes_cooperatives")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DemandeCooperative {

    public enum Statut { PENDING, APPROUVEE, REJETEE }

    @Id
    @GeneratedValue
    private UUID id;

    /** Le compte (VOYAGEUR au moment de la demande) qui deviendra Président si approuvé. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "utilisateur_id", nullable = false)
    private Utilisateur utilisateur;

    @Column(name = "nom_president", nullable = false)
    private String nomPresident;

    @Column(nullable = false)
    private String telephone;

    @Column(nullable = false)
    private String email;

    private String cin;

    @Column(name = "nom_cooperative", nullable = false)
    private String nomCooperative;

    private String ville;

    private String adresse;

    /** Note libre du candidat (motivations, documents en texte/lien...). */
    @Column(columnDefinition = "text")
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private Statut statut = Statut.PENDING;

    /** Renseigné uniquement après un rejet. */
    @Column(name = "motif_rejet")
    private String motifRejet;

    /** Renseignée uniquement après une approbation : la coopérative créée. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cooperative_id")
    private Cooperative cooperative;

    @Column(name = "date_creation", nullable = false, updatable = false)
    private OffsetDateTime dateCreation;

    @Column(name = "date_traitement")
    private OffsetDateTime dateTraitement;

    @PrePersist
    public void prePersist() {
        if (dateCreation == null) dateCreation = OffsetDateTime.now();
    }
}
