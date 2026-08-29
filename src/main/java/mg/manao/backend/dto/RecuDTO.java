package mg.manao.backend.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * DTO du reçu, fortement dénormalisé : utilisé par
 *  - pages/user/RecuVoyageur.jsx (voyageur, détail + PDF + QR)
 *  - pages/VerificationQR.jsx (page publique scannée à l'embarquement)
 *  - pages/Recus.jsx (admin, liste)
 * Champs déduits ligne à ligne de ces trois écrans.
 */
@Getter
@Builder
@AllArgsConstructor
public class RecuDTO {
    private String id;

    @JsonProperty("paiement_id")
    private String paiementId;

    @JsonProperty("reservation_id")
    private String reservationId;

    @JsonProperty("numero_recu")
    private String numeroRecu;

    private String token;

    @JsonProperty("verify_url")
    private String verifyUrl;

    // ── Coopérative ──
    @JsonProperty("cooperative_nom")
    private String cooperativeNom;

    @JsonProperty("cooperative_telephone")
    private String cooperativeTelephone;

    // ── Voyageur ──
    @JsonProperty("voyageur_nom")
    private String voyageurNom;

    @JsonProperty("voyageur_telephone")
    private String voyageurTelephone;

    // ── Voyage ──
    @JsonProperty("ville_depart")
    private String villeDepart;

    @JsonProperty("ville_arrivee")
    private String villeArrivee;

    @JsonProperty("date_depart")
    private String dateDepart;

    @JsonProperty("heure_depart")
    private String heureDepart;

    @JsonProperty("numero_place")
    private Integer numeroPlace;

    // ── Réservation / paiement ──
    @JsonProperty("date_reservation")
    private OffsetDateTime dateReservation;

    // "Confirmée" | "Embarquée" — statut du reçu / de l'embarquement
    private String statut;

    @JsonProperty("statut_reservation_label")
    private String statutReservationLabel;

    @JsonProperty("mode_paiement")
    private String modePaiement;

    private BigDecimal montant;

    @JsonProperty("date_generation")
    private OffsetDateTime dateGeneration;

    @JsonProperty("checked_at")
    private OffsetDateTime checkedAt;

    @JsonProperty("checked_by")
    private String checkedBy;

    // Champs techniques utilisés uniquement pour l'autorisation du contrôleur.
    // Ils ne sont jamais sérialisés dans la réponse JSON.
    @JsonIgnore
    private String utilisateurIdInterne;

    @JsonIgnore
    private String cooperativePresidentIdInterne;
}
