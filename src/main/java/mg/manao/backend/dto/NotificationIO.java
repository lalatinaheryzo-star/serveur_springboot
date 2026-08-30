package mg.manao.backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

public class NotificationIO {

    @Getter
    @Builder
    @AllArgsConstructor
    public static class NotificationDTO {
        private String id;

        @JsonProperty("utilisateur_id")
        private String utilisateurId;

        @JsonProperty("reservation_id")
        private String reservationId;

        private String type;
        private String message;
        private String statut;

        @JsonProperty("date_envoi")
        private OffsetDateTime dateEnvoi;
    }

    /** pages/Notifications.jsx est en lecture seule ; createNotification(payload) est
     * surtout utilisé en interne par AppContext.updateReservationStatus(). */
    @Getter
    @Setter
    public static class NotificationRequest {
        @JsonProperty("utilisateur_id")
        private String utilisateurId;

        @JsonProperty("reservation_id")
        private String reservationId;

        private String type;
        private String message;
        private String statut;
    }
}
