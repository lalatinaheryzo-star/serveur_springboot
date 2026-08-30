package mg.manao.backend.service;

import lombok.RequiredArgsConstructor;
import mg.manao.backend.entity.Cooperative;
import mg.manao.backend.entity.Reservation;
import mg.manao.backend.repository.ReservationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RappelSchedulerService {

    private static final Logger log = LoggerFactory.getLogger(RappelSchedulerService.class);

    private final ReservationRepository reservationRepository;
    private final RappelService rappelService;
    private final EmailService emailService;

    @Scheduled(fixedRate = 60_000)
    @Transactional
    public void verifierEtEnvoyerRappels() {
        List<Reservation> candidates = reservationRepository.findCandidatesRappel(LocalDate.now());

        for (Reservation r : candidates) {
            try {
                Cooperative coop = r.getVoyage().getCooperative();
                if (coop == null || !coop.isRappelActif()) continue;
                if (!rappelService.estMomentDenvoyerLeRappel(r.getVoyage(), coop)) continue;

                r.setRappelEnvoye(true);
                r.setRappelEnvoyeAt(OffsetDateTime.now());
                reservationRepository.save(r);

                // Idem que pour la confirmation de réservation : on force le
                // chargement de l'utilisateur avant l'appel @Async (le voyage est
                // déjà initialisé via r.getVoyage().getCooperative() ci-dessus).
                if (r.getUtilisateur() != null) r.getUtilisateur().getEmail();
                emailService.envoyerRappelVoyage(r);
            } catch (Exception e) {
                log.error("Erreur lors du traitement du rappel pour la réservation {} : {}", r.getId(), e.getMessage());
            }
        }
    }
}