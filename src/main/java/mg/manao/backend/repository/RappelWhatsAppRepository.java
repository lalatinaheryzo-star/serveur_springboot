package mg.manao.backend.repository;

import mg.manao.backend.entity.RappelWhatsApp;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

public interface RappelWhatsAppRepository extends JpaRepository<RappelWhatsApp, UUID> {

    Optional<RappelWhatsApp> findByReservationId(UUID reservationId);

    /**
     * Reprise atomique d'un envoi en échec : une seule instance obtient « 1 ligne modifiée ».
     * Les lignes EN_COURS (envoi peut-être parti) ou ENVOYE ne sont JAMAIS reprises : mieux vaut
     * un rappel manquant qu'un message en double.
     */
    @Modifying
    @Query("UPDATE RappelWhatsApp r SET r.statut = 'EN_COURS', r.tentatives = r.tentatives + 1, "
            + "r.derniereTentativeAt = :maintenant WHERE r.reservationId = :reservationId "
            + "AND r.statut = 'ECHEC' AND r.tentatives < :max AND r.derniereTentativeAt <= :avant")
    int reprendreSiEchec(@Param("reservationId") UUID reservationId,
                         @Param("maintenant") OffsetDateTime maintenant,
                         @Param("avant") OffsetDateTime avant,
                         @Param("max") int max);
}
