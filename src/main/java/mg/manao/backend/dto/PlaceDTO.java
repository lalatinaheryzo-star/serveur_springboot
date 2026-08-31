package mg.manao.backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class PlaceDTO {
    private String id;

    @JsonProperty("numero_place")
    private Integer numeroPlace;

    private String statut; // "disponible" | "reservee"
}
