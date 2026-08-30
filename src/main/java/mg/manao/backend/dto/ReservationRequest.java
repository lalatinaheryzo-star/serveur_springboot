package mg.manao.backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/**
 * services.js expose createReservation(payload) -> POST /reservations, mais cette
 * route n'est appelée nulle part dans le flux actuel du frontend (le flux réel passe
 * exclusivement par POST /places/:id/reserver, cf. PaiementClient.jsx). On l'implémente
 * quand même pour la complétude de l'API (ex. création manuelle par un admin) avec le
 * format le plus probable déduit des autres écrans. [À CONFIRMER] si un usage réel existe.
 */
@Getter
@Setter
public class ReservationRequest {
    @NotBlank(message = "voyage_id requis")
    @JsonProperty("voyage_id")
    private String voyageId;

    @JsonProperty("utilisateur_id")
    private String utilisateurId;

    @JsonProperty("numero_place")
    private Integer numeroPlace;
}
