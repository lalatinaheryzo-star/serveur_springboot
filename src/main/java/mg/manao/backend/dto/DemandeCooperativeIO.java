package mg.manao.backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

public class DemandeCooperativeIO {

    /** Payload de création : POST /demandes-cooperatives (utilisateur authentifié). */
    @Getter
    @Setter
    public static class CreateRequest {
        @NotBlank(message = "Nom du président requis")
        @JsonProperty("nom_president")
        private String nomPresident;

        @NotBlank(message = "Téléphone requis")
        private String telephone;

        @NotBlank(message = "Email requis")
        private String email;

        private String cin;

        @NotBlank(message = "Nom de la coopérative requis")
        @JsonProperty("nom_cooperative")
        private String nomCooperative;

        private String ville;
        private String adresse;
        private String message;
    }

    /** Payload de rejet : PATCH /demandes-cooperatives/{id}/reject */
    @Getter
    @Setter
    public static class RejectRequest {
        private String motif;
    }

    @Getter
    @Builder
    @AllArgsConstructor
    public static class DemandeDTO {
        private String id;
        @JsonProperty("utilisateur_id")
        @Builder.Default
        private String utilisateurId = null;
        @JsonProperty("nom_president")
        private String nomPresident;
        private String telephone;
        private String email;
        private String cin;
        @JsonProperty("nom_cooperative")
        private String nomCooperative;
        private String ville;
        private String adresse;
        private String message;
        private String statut;
        @JsonProperty("motif_rejet")
        private String motifRejet;
        @JsonProperty("cooperative_id")
        private String cooperativeId;
        @JsonProperty("date_creation")
        private OffsetDateTime dateCreation;
        @JsonProperty("date_traitement")
        private OffsetDateTime dateTraitement;
    }
}
