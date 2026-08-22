package mg.manao.backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * pages/Reservations.jsx (admin) lit en priorité r.ville_depart / r.ville_arrivee /
 * r.date_depart / r.prix directement sur l'objet réservation avant de retomber sur
 * une recherche dans `voyages` -> on les dénormalise ici pour éviter un aller-retour
 * supplémentaire côté frontend.
 */
@Getter
@Builder
@AllArgsConstructor
public class ReservationDTO {
    private String id;

    @JsonProperty("utilisateur_id")
    private String utilisateurId;

    @JsonProperty("voyage_id")
    private String voyageId;

    @JsonProperty("place_id")
    private String placeId;

    @JsonProperty("numero_place")
    private Integer numeroPlace;

    private String statut;

    @JsonProperty("date_reservation")
    private OffsetDateTime dateReservation;

    // Nom complet du voyageur — pratique pour l'affichage admin sans jointure côté client.
    private String client;

    // Téléphone du voyageur — utilisé par la liste imprimable des voyageurs (espace Président).
    private String telephone;

    // ── Champs du voyage dénormalisés (lecture directe côté frontend) ──
    @JsonProperty("ville_depart")
    private String villeDepart;

    @JsonProperty("ville_arrivee")
    private String villeArrivee;

    @JsonProperty("date_depart")
    private String dateDepart;

    @JsonProperty("heure_depart")
    private String heureDepart;

    private BigDecimal prix;
}
