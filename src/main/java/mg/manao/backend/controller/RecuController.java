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
        assertOwnerOrAdmin(dto);
        return dto;
    }

    @GetMapping("/reservation/{reservationId}")
    public RecuDTO findByReservation(@PathVariable UUID reservationId) {
        // Le contrôle d'accès (propriétaire / Président de la coopérative / ADMIN) est fait dans le
        // service AVANT toute génération de reçu.
        SecurityUserDetails current = CurrentUser.get();
        if (current == null) throw ApiException.unauthorized("Non authentifié.");
        return recuService.findByReservation(reservationId, current.getId(), current.getRole());
    }

    @PostMapping
    public ResponseEntity<RecuDTO> create(@Valid @RequestBody RecuIO.CreateRecuRequest req) {
        SecurityUserDetails current = CurrentUser.get();
        if (current == null) throw ApiException.unauthorized("Non authentifié.");
        // RecuService.create vérifie que le paiement appartient à l'utilisateur connecté
        // (ou à la coopérative du Président, ou que l'appelant est ADMIN).
        return ResponseEntity.status(201).body(recuService.create(req, current.getId(), current.getRole()));
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<byte[]> download(@PathVariable UUID id) {
        // Une seule lecture optimisée du reçu est nécessaire. Le PDF est ensuite
        // généré directement depuis le DTO déjà chargé.
        RecuDTO dto = recuService.findById(id);
        assertOwnerOrAdmin(dto);
        byte[] pdf = recuService.generatePdf(dto);
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
    // GET /verify/{token} : public (contrôle visuel du billet, données minimales).
    // POST /verify/{token}/checkin : authentification obligatoire (voir SecurityConfig).

    @GetMapping("/verify/{token}")
    public RecuIO.VerifyResponse verify(@PathVariable String token) {
        return recuService.verifyByToken(token);
    }

    /**
     * Validation de l'embarquement : authentification obligatoire (voir SecurityConfig) et réservée
     * au Président de la coopérative concernée ou à l'ADMIN (contrôle dans RecuService.checkin).
     * Le corps (checked_by) est accepté pour compatibilité mais ignoré : l'agent enregistré est
     * l'utilisateur authentifié.
     */
    @PostMapping("/verify/{token}/checkin")
    public RecuIO.CheckinResponse checkin(@PathVariable String token, @RequestBody(required = false) RecuIO.CheckinRequest req) {
        SecurityUserDetails current = CurrentUser.get();
        if (current == null) throw ApiException.unauthorized("Connexion requise pour valider l'embarquement.");
        String agent = ((current.getUtilisateur().getPrenom() == null ? "" : current.getUtilisateur().getPrenom()) + " "
                + (current.getUtilisateur().getNom() == null ? "" : current.getUtilisateur().getNom())).trim();
        return recuService.checkin(token, current.getId(), current.getRole(), agent.isEmpty() ? "Agent" : agent);
    }

    private void assertOwnerOrAdmin(RecuDTO dto) {
        SecurityUserDetails current = CurrentUser.get();
        if (current == null) throw ApiException.unauthorized("Non authentifié.");
        if (current.getRole() == Utilisateur.Role.ADMIN) return;

        if (current.getRole() == Utilisateur.Role.PRESIDENT
                && dto.getCooperativePresidentIdInterne() != null
                && dto.getCooperativePresidentIdInterne().equals(current.getId().toString())) {
            return;
        }

        if (dto.getUtilisateurIdInterne() == null
                || !dto.getUtilisateurIdInterne().equals(current.getId().toString())) {
            throw ApiException.forbidden("Accès refusé à ce reçu.");
        }
    }
}
