package mg.manao.backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
@AllArgsConstructor
public class VoyageDTO {
    private String id;

    @JsonProperty("ville_depart")
    private String villeDepart;

    @JsonProperty("ville_arrivee")
    private String villeArrivee;

    @JsonProperty("date_depart")
    private String dateDepart; // yyyy-MM-dd

    @JsonProperty("heure_depart")
    private String heureDepart; // HH:mm:ss

    private BigDecimal prix;
    private String statut;

    @JsonProperty("cooperative_id")
    private String cooperativeId;

    // Nom de la coopérative dénormalisé pour affichage direct (fallback
    // utilisé par le frontend si la liste des coopératives n'est pas chargée).
    @JsonProperty("cooperative_nom")
    private String cooperativeNom;

    @JsonProperty("vehicule_nom")
    private String vehiculeNom;

    private Integer capacite;

    @JsonProperty("places_disponibles")
    private Integer placesDisponibles;

    @JsonProperty("places_total")
    private Integer placesTotal;
}
