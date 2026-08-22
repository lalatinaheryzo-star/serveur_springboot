package mg.manao.backend.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/** services.js: updatePaiementStatut(id, statut) -> PATCH /paiements/:id/statut { statut } */
@Getter
@Setter
public class UpdatePaiementStatutRequest {
    @NotBlank
    private String statut;
}
