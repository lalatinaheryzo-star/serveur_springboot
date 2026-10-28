package mg.manao.backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Trace d'envoi du rappel WhatsApp d'une réservation (au plus UNE ligne par réservation, garanti
 * par la contrainte unique : c'est ce qui empêche les doublons, même avec plusieurs instances).
 * Migration : database_migration_rappels_whatsapp.sql.
 */
@Entity
@Table(name = "rappels_whatsapp")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RappelWhatsApp {

    public static final String STATUT_EN_COURS = "EN_COURS";
    public static final String STATUT_ENVOYE = "ENVOYE";
    public static final String STATUT_ECHEC = "ECHEC";

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "reservation_id", nullable = false, unique = true)
    private UUID reservationId;

    @Column(nullable = false, length = 20)
    private String statut;

    @Column(nullable = false)
    private int tentatives;

    @Column(name = "derniere_tentative_at", nullable = false)
    private OffsetDateTime derniereTentativeAt;

    @Column(name = "envoye_at")
    private OffsetDateTime envoyeAt;

    /** Cause courte de la dernière erreur (jamais de numéro, de clé ni de corps de requête). */
    @Column(name = "derniere_erreur", length = 200)
    private String derniereErreur;
}
