package mg.manao.backend.repository;

import mg.manao.backend.entity.Recu;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RecuRepository extends JpaRepository<Recu, UUID> {

    // RecuService.toDto() lit recu -> paiement -> reservation -> (voyage -> cooperative,
    // utilisateur) : sans FETCH, ça fait jusqu'à 5 requêtes séparées PAR REÇU (N+1).
    @org.springframework.data.jpa.repository.Query(
        "SELECT r FROM Recu r "
        + "LEFT JOIN FETCH r.paiement p "
        + "LEFT JOIN FETCH p.reservation res "
        + "LEFT JOIN FETCH res.voyage v "
        + "LEFT JOIN FETCH v.cooperative c "
        + "LEFT JOIN FETCH c.president "
        + "LEFT JOIN FETCH res.utilisateur "
        + "ORDER BY r.dateGeneration DESC")
    List<Recu> findAllByOrderByDateGenerationDesc();
    Optional<Recu> findByPaiementId(UUID paiementId);
    Optional<Recu> findByPaiementReservationId(UUID reservationId);
    Optional<Recu> findByToken(String token);


    @org.springframework.data.jpa.repository.Query(
        "SELECT r FROM Recu r "
        + "JOIN FETCH r.paiement p "
        + "JOIN FETCH p.reservation res "
        + "JOIN FETCH res.voyage v "
        + "LEFT JOIN FETCH v.cooperative c "
        + "LEFT JOIN FETCH c.president "
        + "JOIN FETCH res.utilisateur "
        + "LEFT JOIN FETCH res.place "
        + "WHERE r.id = :id")
    Optional<Recu> findByIdWithDetails(UUID id);

    @org.springframework.data.jpa.repository.Query(
        "SELECT r FROM Recu r "
        + "JOIN FETCH r.paiement p "
        + "JOIN FETCH p.reservation res "
        + "JOIN FETCH res.voyage v "
        + "LEFT JOIN FETCH v.cooperative c "
        + "LEFT JOIN FETCH c.president "
        + "JOIN FETCH res.utilisateur "
        + "LEFT JOIN FETCH res.place "
        + "WHERE res.id = :reservationId")
    Optional<Recu> findByReservationIdWithDetails(UUID reservationId);

    @org.springframework.data.jpa.repository.Query(
        "SELECT r FROM Recu r "
        + "JOIN FETCH r.paiement p "
        + "JOIN FETCH p.reservation res "
        + "JOIN FETCH res.voyage v "
        + "LEFT JOIN FETCH v.cooperative c "
        + "LEFT JOIN FETCH c.president "
        + "JOIN FETCH res.utilisateur "
        + "LEFT JOIN FETCH res.place "
        + "WHERE r.token = :token")
    Optional<Recu> findByTokenWithDetails(String token);

    /**
     * Dernier reçu émis pour un préfixe donné (ex. "REC-2026-"), trié par
     * numéro décroissant. Sert à calculer le prochain numéro disponible sans
     * dépendre du nombre total de lignes (count()), qui peut être décorrélé
     * du plus grand numéro déjà attribué (suppression, ré-seed, etc.).
     */
    Optional<Recu> findTopByNumeroRecuStartingWithOrderByNumeroRecuDesc(String prefix);
}
