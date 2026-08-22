package mg.manao.backend.repository;

import mg.manao.backend.entity.Reservation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReservationRepository extends JpaRepository<Reservation, UUID> {

    List<Reservation> findAllByOrderByDateReservationDesc();

    List<Reservation> findByStatutOrderByDateReservationDesc(String statut);

    List<Reservation> findByUtilisateurIdOrderByDateReservationDesc(UUID utilisateurId);

    List<Reservation> findByUtilisateurIdAndStatutOrderByDateReservationDesc(UUID utilisateurId, String statut);

    List<Reservation> findByVoyageId(UUID voyageId);

    Optional<Reservation> findByPlaceId(UUID placeId);

    @org.springframework.data.jpa.repository.Query(
        "SELECT r FROM Reservation r WHERE r.voyage.cooperative.id = :cooperativeId ORDER BY r.dateReservation DESC")
    List<Reservation> findByCooperativeId(UUID cooperativeId);

    @org.springframework.data.jpa.repository.Query(
        "SELECT r FROM Reservation r WHERE r.voyage.cooperative.id = :cooperativeId AND r.statut = :statut ORDER BY r.dateReservation DESC")
        List<Reservation> findByCooperativeIdAndStatut(UUID cooperativeId, String statut);

    /**
     * Candidats au rappel WhatsApp (étape 2) : réservations confirmées, pas
     * encore rappelées, dont le voyage n'est pas déjà passé.
     */
    @org.springframework.data.jpa.repository.Query(
        "SELECT r FROM Reservation r WHERE r.statut = 'Validée' AND r.rappelEnvoye = false "
        + "AND r.voyage.dateDepart >= :auPlusTot")
    List<Reservation> findCandidatesRappel(java.time.LocalDate auPlusTot);
}
