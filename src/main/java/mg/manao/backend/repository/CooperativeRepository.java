package mg.manao.backend.repository;

import mg.manao.backend.entity.Cooperative;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CooperativeRepository extends JpaRepository<Cooperative, UUID> {
    @org.springframework.data.jpa.repository.Query(
        "SELECT c FROM Cooperative c LEFT JOIN FETCH c.president WHERE c.president.id = :presidentId")
    Optional<Cooperative> findByPresidentId(UUID presidentId);

    @org.springframework.data.jpa.repository.Query(
        "SELECT c FROM Cooperative c LEFT JOIN FETCH c.president")
    java.util.List<Cooperative> findAllWithPresident();
}
