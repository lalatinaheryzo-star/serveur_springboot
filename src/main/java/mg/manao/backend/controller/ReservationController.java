package mg.manao.backend.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import mg.manao.backend.dto.ReservationDTO;
import mg.manao.backend.dto.ReservationRequest;
import mg.manao.backend.dto.UpdateReservationStatutRequest;
import mg.manao.backend.entity.Cooperative;
import mg.manao.backend.entity.Utilisateur;
import mg.manao.backend.exception.ApiException;
import mg.manao.backend.security.CurrentUser;
import mg.manao.backend.security.SecurityUserDetails;
import mg.manao.backend.service.CooperativeService;
import mg.manao.backend.service.ReservationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * GET /reservations est utilisé de trois façons par le frontend :
 *  - Admin (pages/Reservations.jsx) : GET /reservations?statut=... -> toutes les réservations (consultation, §1)
 *  - Président (MesReservationsPresident.jsx) : -> uniquement celles de sa coopérative
 *  - Voyageur (getMesReservations)  : GET /reservations?utilisateur_id=... -> les siennes uniquement
 *
 * La restriction est vérifiée réellement côté serveur (§7 de la spec), pas seulement côté frontend.
 */
@RestController
@RequestMapping("/reservations")
@RequiredArgsConstructor
public class ReservationController {

    private final ReservationService reservationService;
    private final CooperativeService cooperativeService;

    @GetMapping
    public List<ReservationDTO> findAll(@RequestParam(required = false) String statut,
                                         @RequestParam(name = "utilisateur_id", required = false) String utilisateurId) {
        SecurityUserDetails current = CurrentUser.get();
        if (current == null) throw ApiException.unauthorized("Non authentifié.");

        if (utilisateurId != null && !utilisateurId.isBlank()) {
            UUID requested = UUID.fromString(utilisateurId);
            if (current.getRole() == Utilisateur.Role.VOYAGEUR && !requested.equals(current.getId())) {
                throw ApiException.forbidden("Vous ne pouvez consulter que vos propres réservations.");
            }
            return reservationService.findForUtilisateur(requested, statut);
        }

        return switch (current.getRole()) {
            case ADMIN -> reservationService.findAllForAdmin(statut);
            case PRESIDENT -> reservationService.findAllForPresident(myCooperativeId(current), statut);
            case VOYAGEUR -> reservationService.findForUtilisateur(current.getId(), statut);
        };
    }

    @GetMapping("/{id}")
    public ReservationDTO findById(@PathVariable UUID id) {
        SecurityUserDetails current = CurrentUser.get();
        if (current == null) throw ApiException.unauthorized("Non authentifié.");

        if (current.getRole() == Utilisateur.Role.PRESIDENT) {
            Cooperative coop = cooperativeService.getEntityForPresident(current.getId());
            return reservationService.findByIdForPresident(id, coop); // 403 si une autre coopérative
        }

        ReservationDTO dto = reservationService.findById(id);
        if (current.getRole() == Utilisateur.Role.VOYAGEUR
                && !dto.getUtilisateurId().equals(current.getId().toString())) {
            throw ApiException.forbidden("Accès refusé à cette réservation.");
        }
        return dto; // ADMIN : accès complet, comportement inchangé
    }

    /** Création manuelle (ex. admin) — le flux voyageur standard passe par POST /places/{id}/reserver. */
    @PostMapping
    public ResponseEntity<ReservationDTO> create(@Valid @RequestBody ReservationRequest req) {
        SecurityUserDetails current = CurrentUser.get();
        if (current == null) throw ApiException.unauthorized("Non authentifié.");
        boolean isAdmin = current.getRole() == Utilisateur.Role.ADMIN;
        // Un voyageur ne peut créer une réservation manuelle que pour lui-même.
        if (!isAdmin) {
            if (req.getUtilisateurId() == null || !req.getUtilisateurId().equals(current.getId().toString())) {
                throw ApiException.forbidden("Vous ne pouvez créer une réservation que pour vous-même.");
            }
        }
        return ResponseEntity.status(201).body(reservationService.createManual(req));
    }

    /** Réservé au Président de la coopérative concernée (§5 et §7 de la spec — plus ADMIN). */
    @PatchMapping("/{id}/statut")
    public ReservationDTO updateStatut(@PathVariable UUID id, @Valid @RequestBody UpdateReservationStatutRequest req) {
        SecurityUserDetails current = CurrentUser.get();
        if (current == null) throw ApiException.unauthorized("Non authentifié.");
        // Une seule requête JOINED côté réservation suffit maintenant à vérifier
        // la propriété de la coopérative, charger les relations nécessaires et
        // préparer la réponse. On supprime ainsi plusieurs aller-retours DB.
        return reservationService.updateStatut(id, req, current.getId());
    }

    /** ADMIN uniquement (voir SecurityConfig). */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        reservationService.delete(id);
        return ResponseEntity.noContent().build();
    }

    private UUID myCooperativeId(SecurityUserDetails current) {
        return cooperativeService.getEntityForPresident(current.getId()).getId();
    }
}
