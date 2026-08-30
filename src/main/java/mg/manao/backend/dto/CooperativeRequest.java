package mg.manao.backend.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CooperativeRequest {
    @NotBlank(message = "Le nom est obligatoire")
    private String nom;

     private String adresse;
    private String telephone;

    // Rappel de voyage : optionnels (types objet, pas primitifs) pour que le
    // Président puisse enregistrer nom/adresse/téléphone SANS toucher au
    // rappel, et inversement — voir CooperativeService.update().
    private Boolean rappelActif;
    private Integer rappelDelaiMinutes;
}
