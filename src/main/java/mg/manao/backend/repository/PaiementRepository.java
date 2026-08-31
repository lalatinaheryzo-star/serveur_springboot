package mg.manao.backend.repository;

import mg.manao.backend.entity.Paiement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaiementRepository extends JpaRepository<Paiement, UUID> {
    @org.springframework.data.jpa.repository.Query(
        "SELECT p FROM Paiement p "
        + "JOIN FETCH p.reservation "
        + "ORDER BY p.datePaiement DESC")
    List<Paiement> findAllByOrderByDatePaiementDesc();
    Optional<Paiement> findByReservationId(UUID reservationId);


    @org.springframework.data.jpa.repository.Query(
        "SELECT p FROM Paiement p "
        + "JOIN FETCH p.reservation res "
        + "JOIN FETCH res.voyage v "
        + "LEFT JOIN FETCH v.cooperative "
        + "JOIN FETCH res.utilisateur "
        + "LEFT JOIN FETCH res.place "
        + "WHERE res.id = :reservationId")
    Optional<Paiement> findByReservationIdWithDetails(UUID reservationId);

    /**
     * Utilisé par DashboardService : calcule le revenu total en base (SUM SQL)
     * au lieu de charger TOUS les paiements en mémoire pour les additionner
     * côté Java à chaque login admin / rafraîchissement du tableau de bord.
     */
    @org.springframework.data.jpa.repository.Query(
        "SELECT COALESCE(SUM(p.montant), 0) FROM Paiement p WHERE p.statut = :statut")
    java.math.BigDecimal sumMontantByStatut(String statut);

    @org.springframework.data.jpa.repository.Query(
        "SELECT p FROM Paiement p "
        + "JOIN FETCH p.reservation res "
        + "JOIN res.voyage v "
        + "WHERE v.cooperative.id = :cooperativeId "
        + "ORDER BY p.datePaiement DESC")
    List<Paiement> findByCooperativeId(UUID cooperativeId);
}
