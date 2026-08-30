package mg.manao.backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Payload envoyé par pages/Voyages.jsx et pages/president/MesVoyagesPresident.jsx
 * (forme "réelle") :
 *   { ville_depart, ville_arrivee, date_depart, heure_depart, prix, statut,
 *     cooperative_id, vehicule_nom, capacite }
 */
@Getter
@Setter
public class VoyageRequest {
    @NotBlank(message = "Ville de départ requise")
    @JsonProperty("ville_depart")
    private String villeDepart;

    @NotBlank(message = "Ville d'arrivée requise")
    @JsonProperty("ville_arrivee")
    private String villeArrivee;

    @NotNull(message = "Date de départ requise")
    @JsonProperty("date_depart")
    private String dateDepart;

    @JsonProperty("heure_depart")
    private String heureDepart;

    private BigDecimal prix;
    private String statut;

    @JsonProperty("cooperative_id")
    private String cooperativeId;

    @JsonProperty("vehicule_nom")
    private String vehiculeNom;

    private Integer capacite;
}
