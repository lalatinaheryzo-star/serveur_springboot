package mg.manao.backend.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import mg.manao.backend.dto.RecuDTO;
import mg.manao.backend.dto.RecuIO;
import mg.manao.backend.entity.Utilisateur;
import mg.manao.backend.exception.ApiException;
import mg.manao.backend.security.CurrentUser;
import mg.manao.backend.security.SecurityUserDetails;
import mg.manao.backend.service.RecuService;
import mg.manao.backend.service.ReservationService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/recus")
@RequiredArgsConstructor
public class RecuController {

    private final RecuService recuService;
    private final ReservationService reservationService;

    /** ADMIN uniquement (liste complète — voir SecurityConfig). */
    @GetMapping
    public List<RecuDTO> findAll() {
        return recuService.findAll();
    }

    @GetMapping("/{id}")
    public RecuDTO findById(@PathVariable UUID id) {
        RecuDTO dto = recuService.findById(id);
        assertOwnerOrAdmin(dto.getReservationId());
        return dto;
    }

    @GetMapping("/reservation/{reservationId}")
    public RecuDTO findByReservation(@PathVariable UUID reservationId) {
        assertOwnerOrAdmin(reservationId.toString());
        return recuService.findByReservation(reservationId);
    }

    @PostMapping
    public ResponseEntity<RecuDTO> create(@Valid @RequestBody RecuIO.CreateRecuRequest req) {
        SecurityUserDetails current = CurrentUser.get();
        if (current == null) throw ApiException.unauthorized("Non authentifié.");
        // Le contrôle fin (le paiement appartient bien à l'utilisateur) est fait via
        // le paiement -> réservation -> utilisateur dans RecuService le cas échéant ;
        // ici on s'assure au minimum que la requête est authentifiée.
        return ResponseEntity.status(201).body(recuService.create(req));
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<byte[]> download(@PathVariable UUID id) {
        RecuDTO dto = recuService.findById(id);
        assertOwnerOrAdmin(dto.getReservationId());
        byte[] pdf = recuService.downloadPdf(id);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + dto.getNumeroRecu() + ".pdf\"")
                .body(pdf);
    }

    /** ADMIN uniquement (voir SecurityConfig). */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        recuService.delete(id);
        return ResponseEntity.noContent().build();
    }

    // ── Page publique de vérification / embarquement (VerificationQR.jsx) ──
    // Aucune authentification requise (voir SecurityConfig) : un agent de gare
    // scanne le QR code sans compte utilisateur.

    @GetMapping("/verify/{token}")
    public RecuIO.VerifyResponse verify(@PathVariable String token) {
        return recuService.verifyByToken(token);
    }

    @PostMapping("/verify/{token}/checkin")
    public RecuIO.CheckinResponse checkin(@PathVariable String token, @RequestBody(required = false) RecuIO.CheckinRequest req) {
        return recuService.checkin(token, req != null ? req : new RecuIO.CheckinRequest());
    }

    private void assertOwnerOrAdmin(String reservationId) {
        SecurityUserDetails current = CurrentUser.get();
        if (current == null) throw ApiException.unauthorized("Non authentifié.");
        if (current.getRole() == Utilisateur.Role.ADMIN) return;
        var reservation = reservationService.getEntity(UUID.fromString(reservationId));
        if (current.getRole() == Utilisateur.Role.PRESIDENT
                && reservation.getVoyage() != null && reservation.getVoyage().getCooperative() != null
                && reservation.getVoyage().getCooperative().getPresident() != null
                && reservation.getVoyage().getCooperative().getPresident().getId().equals(current.getId())) {
            return;
        }
        if (!reservation.getUtilisateur().getId().equals(current.getId())) {
            throw ApiException.forbidden("Accès refusé à ce reçu.");
        }
    }
}
