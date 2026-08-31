package mg.manao.backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * pages/Paiements.jsx et pages/user/PaiementClient.jsx envoient :
 *   { reservation_id, montant, mode_paiement, statut }
 */
@Getter
@Setter
public class PaiementRequest {
    @NotBlank(message = "reservation_id requis")
    @JsonProperty("reservation_id")
    private String reservationId;

    @NotNull(message = "montant requis")
    private BigDecimal montant;

    @JsonProperty("mode_paiement")
    private String modePaiement;

    private String statut;
}
