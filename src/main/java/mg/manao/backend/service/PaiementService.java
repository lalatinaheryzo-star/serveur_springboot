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

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PaiementService {

    /**
     * Transitions autorisées du statut d'un paiement. « Réussi » est final : un paiement confirmé
     * (qui peut avoir donné lieu à un reçu) ne revient pas en arrière.
     */
    static final Map<String, Set<String>> TRANSITIONS = Map.of(
            Paiement.STATUT_EN_ATTENTE, Set.of(Paiement.STATUT_REUSSI, Paiement.STATUT_ECHOUE),
            Paiement.STATUT_ECHOUE, Set.of(Paiement.STATUT_EN_ATTENTE, Paiement.STATUT_REUSSI),
            Paiement.STATUT_REUSSI, Set.of());

    private static final int MODE_PAIEMENT_MAX = 50;

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
     * Enregistre la DÉCLARATION d'un paiement pour une réservation.
     *
     * - Le montant est toujours celui du voyage (prix côté serveur) : le `montant` envoyé par le
     *   client est ignoré.
     * - Le statut initial est toujours « En attente », quel que soit le `statut` envoyé par le
     *   client : aucun paiement n'est « Réussi » sans confirmation. Il n'existe aucune intégration
     *   Mobile Money avec un opérateur : la confirmation (Mobile Money comme caisse) est faite
     *   manuellement par le Président de la coopérative (PATCH /paiements/{id}/statut).
     * - Un seul paiement par réservation (contrôle ici + contrainte unique en base : une demande
     *   simultanée échoue en 409).
     */
    @Transactional
    public PaiementDTO create(PaiementRequest req) {
        if (req.getReservationId() == null || req.getReservationId().isBlank()) {
            throw ApiException.badRequest("reservation_id requis.");
        }
        Reservation reservation = reservationRepository.findById(UUID.fromString(req.getReservationId()))
                .orElseThrow(() -> ApiException.notFound("Réservation introuvable."));

        if (Reservation.STATUT_REFUSEE.equals(reservation.getStatut())
                || Reservation.STATUT_ANNULEE.equals(reservation.getStatut())) {
            throw ApiException.conflict("Cette réservation n'est plus active : aucun paiement ne peut être enregistré.");
        }
        if (paiementRepository.findByReservationId(reservation.getId()).isPresent()) {
            throw ApiException.conflict("Cette réservation possède déjà un paiement.");
        }

        BigDecimal prix = reservation.getVoyage() != null ? reservation.getVoyage().getPrix() : null;
        if (prix == null || prix.signum() < 0) {
            throw ApiException.conflict("Le prix du voyage n'est pas défini : paiement impossible.");
        }

        Paiement paiement = Paiement.builder()
                .reservation(reservation)
                .montant(prix)
                .modePaiement(normaliserMode(req.getModePaiement()))
                .statut(Paiement.STATUT_EN_ATTENTE)
                .build();
        paiement = paiementRepository.save(paiement);

        return toDto(paiement);
    }

    private static String normaliserMode(String mode) {
        if (mode == null || mode.isBlank()) {
            throw ApiException.badRequest("mode_paiement requis.");
        }
        String m = mode.trim();
        if (m.length() > MODE_PAIEMENT_MAX) {
            throw ApiException.badRequest("mode_paiement trop long.");
        }
        return m;
    }

    /**
     * Confirmation / échec d'un paiement par le Président de la coopérative concernée.
     * Le statut doit être connu et la transition autorisée. Passer à « Réussi » ne valide pas la
     * réservation (décision distincte du Président) ; le reçu n'est généré que lorsque le paiement
     * est « Réussi » ET la réservation « Validée » (voir RecuService.ensureRecuForPaiement).
     */
    @Transactional
    public PaiementDTO updateStatut(UUID id, UpdatePaiementStatutRequest req, mg.manao.backend.entity.Cooperative coop) {
        String nouveau = req.getStatut() == null ? "" : req.getStatut().trim();
        if (!TRANSITIONS.containsKey(nouveau)) {
            throw ApiException.badRequest("Statut de paiement invalide : " + nouveau);
        }

        Paiement p = getEntity(id);
        if (p.getReservation() == null || p.getReservation().getVoyage() == null
                || p.getReservation().getVoyage().getCooperative() == null
                || !p.getReservation().getVoyage().getCooperative().getId().equals(coop.getId())) {
            throw ApiException.forbidden("Ce paiement n'appartient pas à votre coopérative.");
        }

        String courant = p.getStatut();
        if (!nouveau.equals(courant)) {
            if (!TRANSITIONS.getOrDefault(courant, Set.of()).contains(nouveau)) {
                throw ApiException.conflict("Changement de statut non autorisé : " + courant + " → " + nouveau + ".");
            }
            if (Paiement.STATUT_REUSSI.equals(nouveau)
                    && (Reservation.STATUT_REFUSEE.equals(p.getReservation().getStatut())
                        || Reservation.STATUT_ANNULEE.equals(p.getReservation().getStatut()))) {
                throw ApiException.conflict("La réservation est refusée ou annulée : le paiement ne peut pas être confirmé.");
            }
            p.setStatut(nouveau);
            paiementRepository.save(p);
        }
        // Génère le reçu seulement si paiement « Réussi » ET réservation « Validée » (idempotent).
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
