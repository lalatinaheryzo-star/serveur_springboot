package mg.manao.backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

public class RecuIO {

    /** services.js: createRecu(paiementId) -> POST /recus { paiement_id } */
    @Getter
    @Setter
    public static class CreateRecuRequest {
        @NotBlank(message = "paiement_id requis")
        @JsonProperty("paiement_id")
        private String paiementId;
    }

    /** services.js: checkinRecuByToken(token, checkedBy) -> POST /recus/verify/:token/checkin { checked_by } */
    @Getter
    @Setter
    public static class CheckinRequest {
        @JsonProperty("checked_by")
        private String checkedBy;
    }

    /**
     * VerificationQR.jsx: verifyRecuByToken(token) -> GET /recus/verify/:token
     *   .then(data => { setRecu(data.recu); setState(data.already_used ? "used" : "valid"); })
     */
    @Getter
    @Builder
    @AllArgsConstructor
    public static class VerifyResponse {
        private RecuDTO recu;

        @JsonProperty("already_used")
        private boolean alreadyUsed;
    }

    /**
     * VerificationQR.jsx: checkinRecuByToken(...).then(data => setRecu(data.recu))
     */
    @Getter
    @Builder
    @AllArgsConstructor
    public static class CheckinResponse {
        private RecuDTO recu;
    }
}

