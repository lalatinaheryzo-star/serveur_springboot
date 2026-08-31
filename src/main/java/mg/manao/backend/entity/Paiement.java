package mg.manao.backend.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "paiements")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Paiement {

    public static final String STATUT_REUSSI     = "Réussi";
    public static final String STATUT_ECHOUE     = "Échoué";
    public static final String STATUT_EN_ATTENTE = "En attente";

    @Id
    @GeneratedValue
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reservation_id", nullable = false, unique = true)
    private Reservation reservation;

    @Column(nullable = false)
    private BigDecimal montant;

    // "Mvola" | "Orange Money" | "Airtel Money" | "Espèces" | "Virement"
    @Column(name = "mode_paiement", nullable = false)
    private String modePaiement;

    @Column(nullable = false)
    @Builder.Default
    private String statut = STATUT_EN_ATTENTE;

    @Column(name = "date_paiement", nullable = false, updatable = false)
    private OffsetDateTime datePaiement;

    @PrePersist
    public void prePersist() {
        if (datePaiement == null) datePaiement = OffsetDateTime.now();
    }
}
