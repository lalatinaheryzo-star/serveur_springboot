package mg.manao.backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/**
 * services.js: updateReservationStatut(id, statut, adminId) -> PATCH /reservations/:id/statut
 *   { statut, admin_id }
 */
@Getter
@Setter
public class UpdateReservationStatutRequest {
    @NotBlank
    private String statut;

    @JsonProperty("admin_id")
    private String adminId;
}
