package mg.manao.backend.service;

import lombok.RequiredArgsConstructor;
import mg.manao.backend.dto.ReservationDTO;
import mg.manao.backend.dto.ReservationRequest;
import mg.manao.backend.dto.UpdateReservationStatutRequest;
import mg.manao.backend.entity.*;
import mg.manao.backend.exception.ApiException;
import mg.manao.backend.repository.PaiementRepository;
import mg.manao.backend.repository.PlaceRepository;
import mg.manao.backend.repository.ReservationRepository;
import mg.manao.backend.repository.UtilisateurRepository;
import mg.manao.backend.repository.VoyageRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ReservationService {

    private final ReservationRepository reservationRepository;
    private final VoyageRepository voyageRepository;
    private final UtilisateurRepository utilisateurRepository;
    private final PlaceRepository placeRepository;
    private final EmailService emailService;
    private final PlaceService placeService;
    private final PaiementRepository paiementRepository;
    private final RecuService recuService;

    /**
     * Transitions autorisées du statut d'une réservation. « Refusée » et « Annulée » sont finales :
     * la place est alors libérée et peut être réservée par quelqu'un d'autre, on ne « ressuscite »
     * donc jamais une réservation (sinon deux réservations actives sur la même place).
     */
    static final Map<String, Set<String>> TRANSITIONS = Map.of(
            Reservation.STATUT_EN_ATTENTE, Set.of(Reservation.STATUT_VALIDEE, Reservation.STATUT_REFUSEE, Reservation.STATUT_ANNULEE),
            Reservation.STATUT_VALIDEE, Set.of(Reservation.STATUT_ANNULEE),
            Reservation.STATUT_REFUSEE, Set.of(),
            Reservation.STATUT_ANNULEE, Set.of());

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

    public static ReservationDTO toDto(Reservation r) {
        Voyage v = r.getVoyage();
        Utilisateur u = r.getUtilisateur();
        return ReservationDTO.builder()
                .id(r.getId().toString())
                .utilisateurId(u != null ? u.getId().toString() : null)
                .voyageId(v != null ? v.getId().toString() : null)
                .placeId(r.getPlace() != null ? r.getPlace().getId().toString() : null)
                .numeroPlace(r.getNumeroPlace())
                .statut(r.getStatut())
                .dateReservation(r.getDateReservation())
                .client(u != null ? (u.getNom() + " " + u.getPrenom()) : null)
                .telephone(u != null ? u.getTelephone() : null)
                .villeDepart(v != null ? v.getVilleDepart() : null)
                .villeArrivee(v != null ? v.getVilleArrivee() : null)
                .dateDepart(v != null && v.getDateDepart() != null ? v.getDateDepart().toString() : null)
                .heureDepart(v != null && v.getHeureDepart() != null ? v.getHeureDepart().format(TIME_FMT) : null)
                .prix(v != null ? v.getPrix() : null)
                .build();
    }

    /** ADMIN : toutes les réservations, avec filtre optionnel par statut. */
    @Transactional(readOnly = true)
    public List<ReservationDTO> findAllForAdmin(String statut) {
        List<Reservation> list = (statut == null || statut.isBlank())
                ? reservationRepository.findAllByOrderByDateReservationDesc()
                : reservationRepository.findByStatutOrderByDateReservationDesc(statut);
        return list.stream().map(ReservationService::toDto).toList();
    }

    /** VOYAGEUR : uniquement ses propres réservations. */
    @Transactional(readOnly = true)
    public List<ReservationDTO> findForUtilisateur(UUID utilisateurId, String statut) {
        List<Reservation> list = (statut == null || statut.isBlank())
                ? reservationRepository.findByUtilisateurIdOrderByDateReservationDesc(utilisateurId)
                : reservationRepository.findByUtilisateurIdAndStatutOrderByDateReservationDesc(utilisateurId, statut);
        return list.stream().map(ReservationService::toDto).toList();
    }

    /** PRESIDENT : uniquement les réservations de SA coopérative (§4 de la spec). */
    @Transactional(readOnly = true)
    public List<ReservationDTO> findAllForPresident(UUID cooperativeId, String statut) {
        List<Reservation> list = (statut == null || statut.isBlank())
                ? reservationRepository.findByCooperativeId(cooperativeId)
                : reservationRepository.findByCooperativeIdAndStatut(cooperativeId, statut);
        return list.stream().map(ReservationService::toDto).toList();
    }

    public Reservation getEntity(UUID id) {
        return reservationRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Réservation introuvable."));
    }

    @Transactional(readOnly = true)
    public ReservationDTO findById(UUID id) {
        return toDto(getEntity(id));
    }

    /** PRESIDENT : consultation d'une réservation précise, limitée à SA coopérative (isolation §9/§12). */
    @Transactional(readOnly = true)
    public ReservationDTO findByIdForPresident(UUID id, Cooperative coop) {
        Reservation r = getEntity(id);
        if (r.getVoyage() == null || r.getVoyage().getCooperative() == null
                || !r.getVoyage().getCooperative().getId().equals(coop.getId())) {
            throw ApiException.forbidden("Cette réservation n'appartient pas à votre coopérative.");
        }
        return toDto(r);
    }

    @Transactional
    public ReservationDTO createManual(ReservationRequest req) {
        if (req.getVoyageId() == null || req.getVoyageId().isBlank()) {
            throw ApiException.badRequest("voyage_id requis.");
        }
        if (req.getUtilisateurId() == null || req.getUtilisateurId().isBlank()) {
            throw ApiException.badRequest("utilisateur_id requis.");
        }
        if (req.getNumeroPlace() == null) {
            throw ApiException.badRequest("numero_place requis.");
        }
        UUID voyageId;
        UUID utilisateurId;
        try {
            voyageId = UUID.fromString(req.getVoyageId().trim());
            utilisateurId = UUID.fromString(req.getUtilisateurId().trim());
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("Identifiant invalide.");
        }
        voyageRepository.findById(voyageId).orElseThrow(() -> ApiException.notFound("Voyage introuvable."));
        Place place = placeRepository.findByVoyageIdAndNumeroPlace(voyageId, req.getNumeroPlace())
                .orElseThrow(() -> ApiException.notFound("Place introuvable pour ce voyage."));
        // Même chemin (verrou pessimiste, contrôles voyage annulé/parti) que POST /places/{id}/reserver.
        return toDto(placeService.reserverPlace(place.getId(), utilisateurId));
    }

    @Transactional
    public ReservationDTO updateStatut(UUID id, UpdateReservationStatutRequest req, UUID presidentId) {
        Reservation r = reservationRepository.findByIdForPresidentWithDetails(id, presidentId)
                .orElseThrow(() -> ApiException.forbidden("Cette réservation n'appartient pas à votre coopérative."));
        String statut = req.getStatut() == null ? "" : req.getStatut().trim();
        if (!TRANSITIONS.containsKey(statut)) {
            throw ApiException.badRequest("Statut de réservation invalide : " + statut);
        }
        String courant = r.getStatut();
        boolean changement = !statut.equals(courant);
        if (changement && !TRANSITIONS.getOrDefault(courant, Set.of()).contains(statut)) {
            throw ApiException.conflict("Changement de statut non autorisé : " + courant + " → " + statut + ".");
        }
        if (changement) {
            r.setStatut(statut);
            // Refus/annulation -> la place redevient disponible et le lien est détaché.
            if (Reservation.STATUT_REFUSEE.equals(statut) || Reservation.STATUT_ANNULEE.equals(statut)) {
                placeService.rendrePlaceDisponible(r);
            }
        }

        Reservation saved = reservationRepository.save(r);

        if (changement && Reservation.STATUT_VALIDEE.equals(statut)) {
            // Si le paiement est déjà « Réussi », le reçu est généré maintenant (idempotent).
            paiementRepository.findByReservationId(saved.getId()).ifPresent(recuService::ensureRecuForPaiement);
            // On force le chargement des relations paresseuses (voyage, utilisateur)
            // AVANT l'appel asynchrone : l'e-mail part sur un thread séparé, une
            // fois cette transaction terminée, donc la session Hibernate ne sera
            // plus ouverte pour résoudre un proxy lazy à ce moment-là.
            if (saved.getVoyage() != null) saved.getVoyage().getVilleArrivee();
            if (saved.getUtilisateur() != null) saved.getUtilisateur().getEmail();
            emailService.envoyerConfirmationReservation(saved);
        }

        return toDto(saved);
    }

    @Transactional
    public void delete(UUID id) {
        Reservation r = getEntity(id);
        // Une réservation encore active libère sa place ; une réservation déjà annulée/refusée n'en a plus.
        placeService.rendrePlaceDisponible(r);
        reservationRepository.deleteById(id);
    }
}