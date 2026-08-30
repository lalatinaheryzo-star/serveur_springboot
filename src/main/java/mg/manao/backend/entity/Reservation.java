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
@Table(name = "reservations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Reservation {

    // "En attente" | "Validée" | "Refusée" | "Annulée"
    public static final String STATUT_EN_ATTENTE = "En attente";
    public static final String STATUT_VALIDEE    = "Validée";
    public static final String STATUT_REFUSEE    = "Refusée";
    public static final String STATUT_ANNULEE    = "Annulée";

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "voyage_id", nullable = false)
    private Voyage voyage;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "utilisateur_id", nullable = false)
    private Utilisateur utilisateur;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "place_id")
    private Place place;

    @Column(name = "numero_place")
    private Integer numeroPlace;

    @Column(nullable = false)
    @Builder.Default
    private String statut = STATUT_EN_ATTENTE;

       @Column(name = "date_reservation", nullable = false, updatable = false)
    private OffsetDateTime dateReservation;

    // ── Rappel WhatsApp (étape 2) : empêche tout envoi en double ──
    @Builder.Default
    @Column(name = "rappel_envoye", nullable = false)
    private boolean rappelEnvoye = false;

    @Column(name = "rappel_envoye_at")
    private OffsetDateTime rappelEnvoyeAt;

    @PrePersist
    public void prePersist() {
        if (dateReservation == null) dateReservation = OffsetDateTime.now();
    }
}