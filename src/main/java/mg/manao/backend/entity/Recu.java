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
@Table(name = "recus")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Recu {

    @Id
    @GeneratedValue
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "paiement_id", nullable = false, unique = true)
    private Paiement paiement;

    @Column(name = "numero_recu", nullable = false, unique = true)
    private String numeroRecu;

    // Jeton opaque utilisé dans l'URL /verify/:token (QR code) — distinct de l'id
    // pour ne jamais exposer l'UUID interne dans un lien public scannable.
    @Column(nullable = false, unique = true)
    private String token;

    // "Confirmée" | "Embarquée"
    @Column(nullable = false)
    @Builder.Default
    private String statut = "Confirmée";

    @Column(name = "checked_at")
    private OffsetDateTime checkedAt;

    @Column(name = "checked_by")
    private String checkedBy;

    @Column(name = "date_generation", nullable = false, updatable = false)
    private OffsetDateTime dateGeneration;

    @PrePersist
    public void prePersist() {
        if (dateGeneration == null) dateGeneration = OffsetDateTime.now();
    }
}
