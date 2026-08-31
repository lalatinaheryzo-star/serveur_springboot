package mg.manao.backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

/**
 * services.js: reservePlace(placeId, utilisateurId) ->
 *   POST /places/:id/reserver { utilisateur_id }
 * Si absent (appel admin depuis Places.jsx), l'utilisateur authentifié est utilisé.
 */
@Getter
@Setter
public class ReserverPlaceRequest {
    @JsonProperty("utilisateur_id")
    private String utilisateurId;
}
