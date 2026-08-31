package mg.manao.backend.controller;

import lombok.RequiredArgsConstructor;
import mg.manao.backend.dto.PlaceDTO;
import mg.manao.backend.dto.ReservationDTO;
import mg.manao.backend.dto.ReserverPlaceRequest;
import mg.manao.backend.entity.Reservation;
import mg.manao.backend.exception.ApiException;
import mg.manao.backend.security.CurrentUser;
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
        UUID utilisateurId = resolveUtilisateurId(req);
        Reservation reservation = placeService.reserverPlace(id, utilisateurId);
        return ResponseEntity.status(201).body(ReservationService.toDto(reservation));
    }

    @PostMapping("/places/{id}/liberer")
    public ResponseEntity<Void> liberer(@PathVariable UUID id) {
        placeService.libererPlace(id);
        return ResponseEntity.noContent().build();
    }

    private UUID resolveUtilisateurId(ReserverPlaceRequest req) {
        if (req != null && req.getUtilisateurId() != null && !req.getUtilisateurId().isBlank()) {
            return UUID.fromString(req.getUtilisateurId());
        }
        var current = CurrentUser.get();
        if (current == null) throw ApiException.unauthorized("Non authentifié.");
        return current.getId();
    }
}
