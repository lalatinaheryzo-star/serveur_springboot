package mg.manao.backend.repository;

import mg.manao.backend.entity.Recu;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RecuRepository extends JpaRepository<Recu, UUID> {
    List<Recu> findAllByOrderByDateGenerationDesc();
    Optional<Recu> findByPaiementId(UUID paiementId);
    Optional<Recu> findByPaiementReservationId(UUID reservationId);
    Optional<Recu> findByToken(String token);

    /**
     * Dernier reçu émis pour un préfixe donné (ex. "REC-2026-"), trié par
     * numéro décroissant. Sert à calculer le prochain numéro disponible sans
     * dépendre du nombre total de lignes (count()), qui peut être décorrélé
     * du plus grand numéro déjà attribué (suppression, ré-seed, etc.).
     */
    Optional<Recu> findTopByNumeroRecuStartingWithOrderByNumeroRecuDesc(String prefix);
}
