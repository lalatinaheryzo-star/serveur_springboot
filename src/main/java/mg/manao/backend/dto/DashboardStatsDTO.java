package mg.manao.backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

/**
 * pages/Dashboard.jsx lit :
 *   stats.total_voyages, stats.total_reservations, stats.reservations_en_attente,
 *   stats.reservations_validees, stats.reservations_refusees, stats.reservations_annulees,
 *   stats.total_utilisateurs, stats.revenu_total
 */
@Getter
@Builder
@AllArgsConstructor
public class DashboardStatsDTO {
    @JsonProperty("total_voyages")
    private long totalVoyages;

    @JsonProperty("total_reservations")
    private long totalReservations;

    @JsonProperty("reservations_en_attente")
    private long reservationsEnAttente;

    @JsonProperty("reservations_validees")
    private long reservationsValidees;

    @JsonProperty("reservations_refusees")
    private long reservationsRefusees;

    @JsonProperty("reservations_annulees")
    private long reservationsAnnulees;

    @JsonProperty("total_utilisateurs")
    private long totalUtilisateurs;

    @JsonProperty("revenu_total")
    private BigDecimal revenuTotal;
}
