package mg.manao.backend.repository;

import mg.manao.backend.entity.Cooperative;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CooperativeRepository extends JpaRepository<Cooperative, UUID> {
    Optional<Cooperative> findByPresidentId(UUID presidentId);
}
