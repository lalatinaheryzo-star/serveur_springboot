package mg.manao.backend.repository;

import mg.manao.backend.entity.Paiement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaiementRepository extends JpaRepository<Paiement, UUID> {
    List<Paiement> findAllByOrderByDatePaiementDesc();
    Optional<Paiement> findByReservationId(UUID reservationId);

    @org.springframework.data.jpa.repository.Query(
        "SELECT p FROM Paiement p WHERE p.reservation.voyage.cooperative.id = :cooperativeId ORDER BY p.datePaiement DESC")
    List<Paiement> findByCooperativeId(UUID cooperativeId);
}
