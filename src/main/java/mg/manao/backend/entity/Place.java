package mg.manao.backend.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "places", uniqueConstraints = @UniqueConstraint(columnNames = {"voyage_id", "numero_place"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Place {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "voyage_id", nullable = false)
    private Voyage voyage;

    @Column(name = "numero_place", nullable = false)
    private Integer numeroPlace;

    // "disponible" | "reservee"
    @Column(nullable = false)
    @Builder.Default
    private String statut = "disponible";
}
