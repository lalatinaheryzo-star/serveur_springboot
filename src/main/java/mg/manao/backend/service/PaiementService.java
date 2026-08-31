package mg.manao.backend.service;

import lombok.RequiredArgsConstructor;
import mg.manao.backend.dto.PaiementDTO;
import mg.manao.backend.dto.PaiementRequest;
import mg.manao.backend.dto.UpdatePaiementStatutRequest;
import mg.manao.backend.entity.Paiement;
import mg.manao.backend.entity.Reservation;
import mg.manao.backend.exception.ApiException;
import mg.manao.backend.repository.PaiementRepository;
import mg.manao.backend.repository.ReservationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PaiementService {

    private final PaiementRepository paiementRepository;
    private final ReservationRepository reservationRepository;
    private final RecuService recuService;

    public static PaiementDTO toDto(Paiement p) {
        return PaiementDTO.builder()
                .id(p.getId().toString())
                .reservationId(p.getReservation().getId().toString())
                .montant(p.getMontant())
                .modePaiement(p.getModePaiement())
                .statut(p.getStatut())
                .datePaiement(p.getDatePaiement())
                .build();
    }

    @Transactional(readOnly = true)
    public List<PaiementDTO> findAll() {
        return paiementRepository.findAllByOrderByDatePaiementDesc().stream().map(PaiementService::toDto).toList();
    }

    /** PRESIDENT : uniquement les paiements des réservations de SA coopérative (§4 de la spec). */
    @Transactional(readOnly = true)
    public List<PaiementDTO> findAllForPresident(UUID cooperativeId) {
        return paiementRepository.findByCooperativeId(cooperativeId).stream().map(PaiementService::toDto).toList();
    }

    public Paiement getEntity(UUID id) {
        return paiementRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Paiement introuvable."));
    }

    public PaiementDTO findById(UUID id) {
        return toDto(getEntity(id));
    }

    /** PRESIDENT : consultation d'un paiement précis, limitée à SA coopérative (isolation §9/§12). */
    @Transactional(readOnly = true)
    public PaiementDTO findByIdForPresident(UUID id, mg.manao.backend.entity.Cooperative coop) {
        Paiement p = getEntity(id);
        if (p.getReservation() == null || p.getReservation().getVoyage() == null
                || p.getReservation().getVoyage().getCooperative() == null
                || !p.getReservation().getVoyage().getCooperative().getId().equals(coop.getId())) {
            throw ApiException.forbidden("Ce paiement n'appartient pas à votre coopérative.");
        }
        return toDto(p);
    }

    /** VOYAGEUR : ne peut consulter que le paiement lié à SA PROPRE réservation. */
    @Transactional(readOnly = true)
    public PaiementDTO findByIdForUtilisateur(UUID id, UUID utilisateurId) {
        Paiement p = getEntity(id);
        if (p.getReservation() == null || p.getReservation().getUtilisateur() == null
                || !p.getReservation().getUtilisateur().getId().equals(utilisateurId)) {
            throw ApiException.forbidden("Accès refusé à ce paiement.");
        }
        return toDto(p);
    }

    /**
     * Le frontend (PaiementClient.jsx) simule directement la passerelle de
     * paiement : il envoie `statut` = "Réussi" ou "En attente" (espèces au
     * guichet) sans étape de callback serveur. [À CONFIRMER] si un vrai
     * fournisseur de paiement (Mvola/Orange Money/Airtel Money — §35 de la
     * spec) doit être intégré ; en l'état, on persiste fidèlement ce que le
     * frontend envoie.
     *
     * Règle métier (workflow §4/§5/§6/§7 de la spec "correction isolation +
     * workflow paiement/validation") : un paiement "Réussi" NE valide PLUS
     * automatiquement la réservation. La réservation reste "En attente"
     * jusqu'à la décision explicite du Président de la coopérative (voir
     * ReservationService.updateStatut, appelé depuis PATCH /reservations/{id}/statut).
     */
    @Transactional
    public PaiementDTO create(PaiementRequest req) {
        if (req.getReservationId() == null || req.getReservationId().isBlank()) {
            throw ApiException.badRequest("reservation_id requis.");
        }
        Reservation reservation = reservationRepository.findById(UUID.fromString(req.getReservationId()))
                .orElseThrow(() -> ApiException.notFound("Réservation introuvable."));

        if (paiementRepository.findByReservationId(reservation.getId()).isPresent()) {
            throw ApiException.conflict("Cette réservation possède déjà un paiement.");
        }

        Paiement paiement = Paiement.builder()
                .reservation(reservation)
                .montant(req.getMontant())
                .modePaiement(req.getModePaiement())
                .statut((req.getStatut() == null || req.getStatut().isBlank()) ? Paiement.STATUT_EN_ATTENTE : req.getStatut())
                .build();
        paiement = paiementRepository.save(paiement);

        return toDto(paiement);
    }

    @Transactional
    public PaiementDTO updateStatut(UUID id, UpdatePaiementStatutRequest req, mg.manao.backend.entity.Cooperative coop) {
        Paiement p = getEntity(id);
        if (p.getReservation() == null || p.getReservation().getVoyage() == null
                || p.getReservation().getVoyage().getCooperative() == null
                || !p.getReservation().getVoyage().getCooperative().getId().equals(coop.getId())) {
            throw ApiException.forbidden("Ce paiement n'appartient pas à votre coopérative.");
        }
        p.setStatut(req.getStatut());
        paiementRepository.save(p);
        // Valider un paiement ne valide plus la réservation automatiquement :
        // c'est désormais une décision distincte et explicite du Président,
        // via PATCH /reservations/{id}/statut (voir ReservationService.updateStatut).
        // En revanche, dès qu'un paiement passe à "Réussi" (ex: encaissement en
        // espèces confirmé au guichet), on génère son reçu immédiatement au lieu
        // d'attendre que le voyageur ouvre la page du reçu (voir RecuService).
        recuService.ensureRecuForPaiement(p);
        return toDto(p);
    }

    @Transactional
    public void delete(UUID id) {
        if (!paiementRepository.existsById(id)) {
            throw ApiException.notFound("Paiement introuvable.");
        }
        paiementRepository.deleteById(id);
    }
}
