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
        long enAttente = reservationRepository.findByStatutOrderByDateReservationDesc(Reservation.STATUT_EN_ATTENTE).size();
        long validees  = reservationRepository.findByStatutOrderByDateReservationDesc(Reservation.STATUT_VALIDEE).size();
        long refusees   = reservationRepository.findByStatutOrderByDateReservationDesc(Reservation.STATUT_REFUSEE).size();
        long annulees   = reservationRepository.findByStatutOrderByDateReservationDesc(Reservation.STATUT_ANNULEE).size();

        BigDecimal revenuTotal = paiementRepository.findAllByOrderByDatePaiementDesc().stream()
                .filter(p -> Paiement.STATUT_REUSSI.equals(p.getStatut()))
                .map(Paiement::getMontant)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

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
