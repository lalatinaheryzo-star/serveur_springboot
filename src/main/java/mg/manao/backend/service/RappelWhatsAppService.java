package mg.manao.backend.service;

import mg.manao.backend.entity.RappelWhatsApp;
import mg.manao.backend.entity.Reservation;
import mg.manao.backend.repository.RappelWhatsAppRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.OffsetDateTime;

/**
 * Envoi du rappel WhatsApp avec trace et anti-doublon.
 *
 * 1. Réservation de l'envoi (transaction courte) : insertion d'une ligne EN_COURS — la contrainte
 *    unique sur reservation_id fait échouer toute seconde instance — ou reprise atomique d'une
 *    ligne ECHEC (max 3 tentatives, délai minimal entre deux essais).
 * 2. Appel HTTP à Evolution API HORS transaction.
 * 3. Marquage ENVOYE seulement si Evolution a accepté le message, sinon ECHEC (+ cause courte).
 *
 * Garantie « au plus une fois » : une ligne restée EN_COURS (crash en plein envoi) n'est jamais
 * rejouée automatiquement.
 */
@Service
public class RappelWhatsAppService {

    private static final Logger log = LoggerFactory.getLogger(RappelWhatsAppService.class);
    static final int MAX_TENTATIVES = 3;

    private final RappelWhatsAppRepository repository;
    private final WhatsAppService whatsAppService;
    private final TransactionTemplate tx;
    private final long delaiReprise;

    public RappelWhatsAppService(RappelWhatsAppRepository repository, WhatsAppService whatsAppService,
                                 TransactionTemplate tx,
                                 @Value("${app.rappel.whatsapp.retry-delay-minutes:5}") long delaiRepriseMinutes) {
        this.repository = repository;
        this.whatsAppService = whatsAppService;
        this.tx = tx;
        this.delaiReprise = delaiRepriseMinutes;
    }

    /** @return true si un message a été accepté par Evolution API pour cette réservation. */
    public boolean envoyerSiNecessaire(Reservation reservation) {
        if (!whatsAppService.estActif()) return false;
        if (!reserverEnvoi(reservation)) return false;

        WhatsAppService.ResultatEnvoi resultat;
        try {
            resultat = whatsAppService.envoyerRappelVoyage(reservation);
        } catch (RuntimeException e) {
            resultat = new WhatsAppService.ResultatEnvoi(WhatsAppService.Statut.ECHEC, e.getClass().getSimpleName());
        }
        final WhatsAppService.ResultatEnvoi r = resultat;
        tx.executeWithoutResult(s -> repository.findByReservationId(reservation.getId()).ifPresent(ligne -> {
            ligne.setDerniereTentativeAt(OffsetDateTime.now());
            if (r.estEnvoye()) {
                ligne.setStatut(RappelWhatsApp.STATUT_ENVOYE);
                ligne.setEnvoyeAt(OffsetDateTime.now());
                ligne.setDerniereErreur(null);
            } else {
                ligne.setStatut(RappelWhatsApp.STATUT_ECHEC);
                ligne.setDerniereErreur(tronquer(r.detail()));
                if (r.statut() == WhatsAppService.Statut.NUMERO_INVALIDE) {
                    ligne.setTentatives(MAX_TENTATIVES); // inutile de réessayer : le numéro ne changera pas
                }
            }
            repository.save(ligne);
        }));
        return r.estEnvoye();
    }

    private boolean reserverEnvoi(Reservation reservation) {
        try {
            Boolean ok = tx.execute(s -> {
                OffsetDateTime maintenant = OffsetDateTime.now();
                var existante = repository.findByReservationId(reservation.getId());
                if (existante.isEmpty()) {
                    repository.saveAndFlush(RappelWhatsApp.builder()
                            .reservationId(reservation.getId())
                            .statut(RappelWhatsApp.STATUT_EN_COURS)
                            .tentatives(1)
                            .derniereTentativeAt(maintenant)
                            .build());
                    return true;
                }
                return repository.reprendreSiEchec(reservation.getId(), maintenant,
                        maintenant.minusMinutes(delaiReprise), MAX_TENTATIVES) == 1;
            });
            return Boolean.TRUE.equals(ok);
        } catch (DataIntegrityViolationException e) {
            return false; // une autre instance a pris l'envoi
        } catch (RuntimeException e) {
            log.error("Réservation de l'envoi WhatsApp impossible pour {} : {}", reservation.getId(), e.getClass().getSimpleName());
            return false;
        }
    }

    private static String tronquer(String s) {
        if (s == null) return null;
        return s.length() <= 200 ? s : s.substring(0, 200);
    }
}
