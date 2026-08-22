package mg.manao.backend.repository;

import mg.manao.backend.entity.Voyage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface VoyageRepository extends JpaRepository<Voyage, UUID> {

    @Query("SELECT v FROM Voyage v LEFT JOIN FETCH v.cooperative ORDER BY v.dateDepart ASC, v.heureDepart ASC")
    List<Voyage> findAllOrdered();

    List<Voyage> findByCooperativeId(UUID cooperativeId);
}
