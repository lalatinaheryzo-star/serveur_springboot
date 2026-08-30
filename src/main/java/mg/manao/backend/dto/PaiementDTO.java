package mg.manao.backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Getter
@Builder
@AllArgsConstructor
public class PaiementDTO {
    private String id;

    @JsonProperty("reservation_id")
    private String reservationId;

    private BigDecimal montant;

    @JsonProperty("mode_paiement")
    private String modePaiement;

    private String statut;

    @JsonProperty("date_paiement")
    private OffsetDateTime datePaiement;
}
