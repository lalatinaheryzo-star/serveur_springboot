package mg.manao.backend.service;

import lombok.RequiredArgsConstructor;
import mg.manao.backend.dto.DashboardStatsDTO;
import mg.manao.backend.entity.Paiement;
import mg.manao.backend.entity.Reservation;
import mg.manao.backend.repository.*;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final VoyageRepository voyageRepository;
    private final ReservationRepository reservationRepository;
    private final UtilisateurRepository utilisateurRepository;
    private final PaiementRepository paiementRepository;

    public DashboardStatsDTO getStats() {
        long totalReservations = reservationRepository.count();
        // Avant : 4 requêtes chargeant CHAQUE réservation (+ voyage + utilisateur +
        // place, avec le JOIN FETCH) juste pour appeler .size() dessus. Remplacé par
        // 4 COUNT SQL légers : aucune ligne ni relation n'est transférée du tout.
        long enAttente = reservationRepository.countByStatut(Reservation.STATUT_EN_ATTENTE);
        long validees  = reservationRepository.countByStatut(Reservation.STATUT_VALIDEE);
        long refusees   = reservationRepository.countByStatut(Reservation.STATUT_REFUSEE);
        long annulees   = reservationRepository.countByStatut(Reservation.STATUT_ANNULEE);

        // Avant : chargeait TOUS les paiements en mémoire pour les additionner en
        // Java. Remplacé par un SUM SQL (agrégation faite par la base, une seule
        // valeur transférée).
        BigDecimal revenuTotal = paiementRepository.sumMontantByStatut(Paiement.STATUT_REUSSI);

        return DashboardStatsDTO.builder()
                .totalVoyages(voyageRepository.count())
                .totalReservations(totalReservations)
                .reservationsEnAttente(enAttente)
                .reservationsValidees(validees)
                .reservationsRefusees(refusees)
                .reservationsAnnulees(annulees)
                .totalUtilisateurs(utilisateurRepository.count())
                .revenuTotal(revenuTotal)
                .build();
    }
}
