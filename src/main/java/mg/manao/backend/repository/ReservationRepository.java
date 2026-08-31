package mg.manao.backend.repository;

import mg.manao.backend.entity.Reservation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReservationRepository extends JpaRepository<Reservation, UUID> {

    // Toutes les méthodes listant des réservations pour l'écran (admin, président,
    // voyageur) utilisent JOIN FETCH sur voyage/utilisateur/place : ReservationService.toDto()
    // accède systématiquement à ces 3 relations pour construire le DTO. Sans FETCH,
    // chaque relation @ManyToOne(LAZY) déclenche une requête SQL séparée PAR LIGNE
    // (N+1) : 20 réservations = 1 + jusqu'à 60 requêtes au lieu d'une seule. Avec le
    // pooler distant (Supabase), chaque aller-retour réseau coûte plusieurs dizaines
    // de ms, d'où la lenteur perçue sur les listes de réservations/reçus.

    @org.springframework.data.jpa.repository.Query(
        "SELECT r FROM Reservation r LEFT JOIN FETCH r.voyage LEFT JOIN FETCH r.utilisateur LEFT JOIN FETCH r.place "
        + "ORDER BY r.dateReservation DESC, r.id ASC")
    List<Reservation> findAllByOrderByDateReservationDesc();

    @org.springframework.data.jpa.repository.Query(
        "SELECT r FROM Reservation r LEFT JOIN FETCH r.voyage LEFT JOIN FETCH r.utilisateur LEFT JOIN FETCH r.place "
        + "WHERE r.statut = :statut ORDER BY r.dateReservation DESC, r.id ASC")
    List<Reservation> findByStatutOrderByDateReservationDesc(String statut);

    @org.springframework.data.jpa.repository.Query(
        "SELECT r FROM Reservation r LEFT JOIN FETCH r.voyage LEFT JOIN FETCH r.utilisateur LEFT JOIN FETCH r.place "
        + "WHERE r.utilisateur.id = :utilisateurId ORDER BY r.dateReservation DESC, r.id ASC")
    List<Reservation> findByUtilisateurIdOrderByDateReservationDesc(UUID utilisateurId);

    @org.springframework.data.jpa.repository.Query(
        "SELECT r FROM Reservation r LEFT JOIN FETCH r.voyage LEFT JOIN FETCH r.utilisateur LEFT JOIN FETCH r.place "
        + "WHERE r.utilisateur.id = :utilisateurId AND r.statut = :statut ORDER BY r.dateReservation DESC, r.id ASC")
    List<Reservation> findByUtilisateurIdAndStatutOrderByDateReservationDesc(UUID utilisateurId, String statut);

    List<Reservation> findByVoyageId(UUID voyageId);

    Optional<Reservation> findByPlaceId(UUID placeId);


    @org.springframework.data.jpa.repository.Query(
        "SELECT r FROM Reservation r "
        + "JOIN FETCH r.voyage v "
        + "JOIN FETCH v.cooperative c "
        + "LEFT JOIN FETCH c.president "
        + "JOIN FETCH r.utilisateur "
        + "LEFT JOIN FETCH r.place "
        + "WHERE r.id = :id AND c.president.id = :presidentId")
    Optional<Reservation> findByIdForPresidentWithDetails(UUID id, UUID presidentId);

    /** Utilisé par DashboardService : un simple COUNT SQL, sans charger les lignes ni leurs relations. */
    long countByStatut(String statut);

    @org.springframework.data.jpa.repository.Query(
        "SELECT r FROM Reservation r LEFT JOIN FETCH r.voyage v LEFT JOIN FETCH r.utilisateur LEFT JOIN FETCH r.place "
        + "WHERE v.cooperative.id = :cooperativeId ORDER BY r.dateReservation DESC, r.id ASC")
    List<Reservation> findByCooperativeId(UUID cooperativeId);

    @org.springframework.data.jpa.repository.Query(
        "SELECT r FROM Reservation r LEFT JOIN FETCH r.voyage v LEFT JOIN FETCH r.utilisateur LEFT JOIN FETCH r.place "
        + "WHERE v.cooperative.id = :cooperativeId AND r.statut = :statut ORDER BY r.dateReservation DESC, r.id ASC")
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
