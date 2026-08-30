package mg.manao.backend.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

@Entity
@Table(name = "voyages")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Voyage {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "ville_depart", nullable = false)
    private String villeDepart;

    @Column(name = "ville_arrivee", nullable = false)
    private String villeArrivee;

    @Column(name = "date_depart", nullable = false)
    private LocalDate dateDepart;

    @Column(name = "heure_depart", nullable = false)
    private LocalTime heureDepart;

    @Column(nullable = false)
    private BigDecimal prix;

    // "actif" | "complet" | "annulé"
    @Column(nullable = false)
    @Builder.Default
    private String statut = "actif";

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cooperative_id")
    private Cooperative cooperative;

    @Column(name = "vehicule_nom")
    @Builder.Default
    private String vehiculeNom = "Sprinter";

    @Column(nullable = false)
    @Builder.Default
    private Integer capacite = 18;
}
