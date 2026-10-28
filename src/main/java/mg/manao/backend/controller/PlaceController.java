package mg.manao.backend.controller;

import lombok.RequiredArgsConstructor;
import mg.manao.backend.dto.PlaceDTO;
import mg.manao.backend.dto.ReservationDTO;
import mg.manao.backend.dto.ReserverPlaceRequest;
import mg.manao.backend.entity.Reservation;
import mg.manao.backend.exception.ApiException;
import mg.manao.backend.security.CurrentUser;
import mg.manao.backend.security.SecurityUserDetails;
import mg.manao.backend.service.PlaceService;
import mg.manao.backend.service.ReservationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class PlaceController {

    private final PlaceService placeService;

    /** GET /voyages/{id}/places — public, cf. Places.jsx (admin) & PlaceClient.jsx (voyageur). */
    @GetMapping("/voyages/{voyageId}/places")
    public List<PlaceDTO> findByVoyage(@PathVariable UUID voyageId) {
        return placeService.findByVoyage(voyageId);
    }

    /**
     * POST /places/{id}/reserver — authentifié. Réserve la place ET crée la
     * réservation en une seule transaction atomique (voir PaiementClient.jsx,
     * commentaire "CORRECTION 409"). Renvoie directement la réservation créée.
     */
    @PostMapping("/places/{id}/reserver")
    public ResponseEntity<ReservationDTO> reserver(@PathVariable UUID id, @RequestBody(required = false) ReserverPlaceRequest req) {
        SecurityUserDetails current = requireCurrent();
        UUID utilisateurId = resolveUtilisateurId(req, current);
        Reservation reservation = placeService.reserverPlace(id, utilisateurId);
        return ResponseEntity.status(201).body(ReservationService.toDto(reservation));
    }

    @PostMapping("/places/{id}/liberer")
    public ResponseEntity<Void> liberer(@PathVariable UUID id) {
        SecurityUserDetails current = requireCurrent();
        placeService.libererPlace(id, current.getId(), current.getRole());
        return ResponseEntity.noContent().build();
    }

    private SecurityUserDetails requireCurrent() {
        SecurityUserDetails current = CurrentUser.get();
        if (current == null) throw ApiException.unauthorized("Non authentifié.");
        return current;
    }

    /**
     * L'identité vient de l'authentification. `utilisateur_id` (fourni par le client) n'est
     * accepté que s'il est identique à l'utilisateur connecté, ou si l'appelant est ADMIN
     * (réservation faite par l'admin pour un voyageur).
     */
    private UUID resolveUtilisateurId(ReserverPlaceRequest req, SecurityUserDetails current) {
        if (req != null && req.getUtilisateurId() != null && !req.getUtilisateurId().isBlank()) {
            UUID demande = UUID.fromString(req.getUtilisateurId().trim());
            if (!demande.equals(current.getId())) {
                if (current.getRole() != mg.manao.backend.entity.Utilisateur.Role.ADMIN) {
                    throw ApiException.forbidden("Vous ne pouvez réserver une place qu'en votre propre nom.");
                }
                return demande;
            }
        }
        return current.getId();
    }
}
