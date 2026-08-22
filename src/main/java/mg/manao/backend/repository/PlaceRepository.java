package mg.manao.backend.repository;

import mg.manao.backend.entity.Place;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PlaceRepository extends JpaRepository<Place, UUID> {

    List<Place> findByVoyageIdOrderByNumeroPlaceAsc(UUID voyageId);

    Optional<Place> findByVoyageIdAndNumeroPlace(UUID voyageId, Integer numeroPlace);

    long countByVoyageIdAndStatut(UUID voyageId, String statut);

    // Verrou pessimiste pour empêcher deux voyageurs de réserver la même place
    // en même temps (condition de course résolue au niveau base de données).
    @Query("SELECT p FROM Place p WHERE p.id = :id")
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    Optional<Place> findByIdForUpdate(@Param("id") UUID id);
}
