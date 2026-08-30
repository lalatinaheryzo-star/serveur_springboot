package mg.manao.backend.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "notifications")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Notification {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "utilisateur_id")
    private Utilisateur utilisateur;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reservation_id")
    private Reservation reservation;

    // "SMS / WhatsApp" | "Email" | ...
    @Column(nullable = false)
    private String type;

    @Column(nullable = false, columnDefinition = "text")
    private String message;

    // "Envoyé" | "Échoué"
    @Column(nullable = false)
    @Builder.Default
    private String statut = "Envoyé";

    @Column(name = "date_envoi", nullable = false, updatable = false)
    private OffsetDateTime dateEnvoi;

    @PrePersist
    public void prePersist() {
        if (dateEnvoi == null) dateEnvoi = OffsetDateTime.now();
    }
}
