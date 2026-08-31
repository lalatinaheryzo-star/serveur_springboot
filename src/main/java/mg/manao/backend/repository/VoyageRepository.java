package mg.manao.backend.repository;

import mg.manao.backend.entity.Voyage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface VoyageRepository extends JpaRepository<Voyage, UUID> {

    // Tri secondaire sur l'id : sans lui, les voyages ayant la même date/heure de
    // départ (fréquent) n'ont pas d'ordre garanti entre deux exécutions de la
    // requête -> le frontend (qui reactualise toutes les 2s) affichait les
    // voyages dans un ordre différent à chaque fois, donnant l'impression que
    // les cartes se déplaçaient/changeaient sans raison.
    @Query("SELECT v FROM Voyage v LEFT JOIN FETCH v.cooperative ORDER BY v.dateDepart ASC, v.heureDepart ASC, v.id ASC")
    List<Voyage> findAllOrdered();

    List<Voyage> findByCooperativeId(UUID cooperativeId);
}
