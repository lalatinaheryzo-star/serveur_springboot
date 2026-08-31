package mg.manao.backend.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import mg.manao.backend.dto.PaiementDTO;
import mg.manao.backend.dto.PaiementRequest;
import mg.manao.backend.dto.UpdatePaiementStatutRequest;
import mg.manao.backend.entity.Cooperative;
import mg.manao.backend.entity.Reservation;
import mg.manao.backend.entity.Utilisateur;
import mg.manao.backend.exception.ApiException;
import mg.manao.backend.security.CurrentUser;
import mg.manao.backend.security.SecurityUserDetails;
import mg.manao.backend.service.CooperativeService;
import mg.manao.backend.service.PaiementService;
import mg.manao.backend.service.ReservationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * GET /paiements : ADMIN voit tout (consultation, §1), PRESIDENT voit uniquement
 * les paiements de sa coopérative (§4). POST reste authentifié (un VOYAGEUR ne
 * paie que sa propre réservation). PATCH /statut est désormais réservé au
 * Président (§4 : "valider les paiements" — plus ADMIN, voir §7).
 */
@RestController
@RequestMapping("/paiements")
@RequiredArgsConstructor
public class PaiementController {

    private final PaiementService paiementService;
    private final ReservationService reservationService;
    private final CooperativeService cooperativeService;

    @GetMapping
    public java.util.List<PaiementDTO> findAll() {
        SecurityUserDetails current = CurrentUser.get();
        if (current == null) throw ApiException.unauthorized("Non authentifié.");
        if (current.getRole() == Utilisateur.Role.PRESIDENT) {
            Cooperative coop = cooperativeService.getEntityForPresident(current.getId());
            return paiementService.findAllForPresident(coop.getId());
        }
        return paiementService.findAll();
    }

    @GetMapping("/{id}")
    public PaiementDTO findById(@PathVariable UUID id) {
        SecurityUserDetails current = CurrentUser.get();
        if (current == null) throw ApiException.unauthorized("Non authentifié.");
        if (current.getRole() == Utilisateur.Role.PRESIDENT) {
            Cooperative coop = cooperativeService.getEntityForPresident(current.getId());
            return paiementService.findByIdForPresident(id, coop); // 403 si une autre coopérative
        }
        if (current.getRole() == Utilisateur.Role.VOYAGEUR) {
            return paiementService.findByIdForUtilisateur(id, current.getId()); // 403 si ce n'est pas sa réservation
        }
        return paiementService.findById(id); // ADMIN : accès complet, comportement inchangé
    }

    @PostMapping
    public ResponseEntity<PaiementDTO> create(@Valid @RequestBody PaiementRequest req) {
        SecurityUserDetails current = CurrentUser.get();
        if (current == null) throw ApiException.unauthorized("Non authentifié.");

        if (current.getRole() == Utilisateur.Role.VOYAGEUR) {
            Reservation reservation = reservationService.getEntity(UUID.fromString(req.getReservationId()));
            if (!reservation.getUtilisateur().getId().equals(current.getId())) {
                throw ApiException.forbidden("Vous ne pouvez payer que votre propre réservation.");
            }
        }
        return ResponseEntity.status(201).body(paiementService.create(req));
    }

    /** Réservé au Président de la coopérative concernée (§4 et §7 de la spec). */
    @PatchMapping("/{id}/statut")
    public PaiementDTO updateStatut(@PathVariable UUID id, @Valid @RequestBody UpdatePaiementStatutRequest req) {
        SecurityUserDetails current = CurrentUser.get();
        if (current == null) throw ApiException.unauthorized("Non authentifié.");
        Cooperative coop = cooperativeService.getEntityForPresident(current.getId());
        return paiementService.updateStatut(id, req, coop);
    }

    /** ADMIN uniquement (voir SecurityConfig). */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        paiementService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
