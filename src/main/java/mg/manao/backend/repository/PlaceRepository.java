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

    // Utilisé par VoyageService.findAll() : récupère en UNE requête les places
    // de TOUS les voyages de la liste, au lieu d'une requête par voyage
    // (l'ancienne implémentation appelait findByVoyageIdOrderByNumeroPlaceAsc
    // dans une boucle -> N+1 : 50 voyages = 51 requêtes SQL pour afficher une
    // simple liste). Le regroupement par voyage se fait ensuite en mémoire.
    List<Place> findByVoyageIdIn(List<UUID> voyageIds);

    Optional<Place> findByVoyageIdAndNumeroPlace(UUID voyageId, Integer numeroPlace);

    long countByVoyageIdAndStatut(UUID voyageId, String statut);

    // Verrou pessimiste pour empêcher deux voyageurs de réserver la même place
    // en même temps (condition de course résolue au niveau base de données).
    @Query("SELECT p FROM Place p WHERE p.id = :id")
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    Optional<Place> findByIdForUpdate(@Param("id") UUID id);
}
