package mg.manao.backend.service;

import lombok.RequiredArgsConstructor;
import mg.manao.backend.entity.Cooperative;
import mg.manao.backend.entity.Reservation;
import mg.manao.backend.repository.ReservationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

/**
 * Déclenche les rappels de voyage (e-mail + WhatsApp). Volontairement SANS transaction englobante :
 * les appels HTTP vers Evolution API ne doivent pas retenir une connexion du pool (5 connexions).
 * Un canal en échec n'empêche pas l'autre ni les réservations suivantes.
 */
@Service
@RequiredArgsConstructor
public class RappelSchedulerService {

    private static final Logger log = LoggerFactory.getLogger(RappelSchedulerService.class);
    private static final ZoneId FUSEAU = ZoneId.of("Indian/Antananarivo");

    private final ReservationRepository reservationRepository;
    private final RappelService rappelService;
    private final EmailService emailService;
    private final RappelWhatsAppService rappelWhatsAppService;

    @Scheduled(fixedRate = 60_000)
    public void verifierEtEnvoyerRappels() {
        List<Reservation> candidates = reservationRepository.findCandidatesRappel(LocalDate.now(FUSEAU));

        for (Reservation r : candidates) {
            try {
                Cooperative coop = r.getVoyage().getCooperative();
                if (coop == null || !coop.isRappelActif()) continue;
                if (!rappelService.estMomentDenvoyerLeRappel(r.getVoyage(), coop)) continue;

                // Canal e-mail : envoi asynchrone (non confirmable ici) ; le drapeau signifie « mis en file ».
                if (!r.isRappelEnvoye()) {
                    r.setRappelEnvoye(true);
                    r.setRappelEnvoyeAt(OffsetDateTime.now());
                    reservationRepository.save(r);
                    emailService.envoyerRappelVoyage(r);
                }

                // Canal WhatsApp : trace en base, marqué « envoyé » seulement si Evolution a accepté.
                rappelWhatsAppService.envoyerSiNecessaire(r);
            } catch (Exception e) {
                log.error("Erreur lors du traitement du rappel pour la réservation {} : {}",
                        r.getId(), e.getClass().getSimpleName());
            }
        }
    }
}
