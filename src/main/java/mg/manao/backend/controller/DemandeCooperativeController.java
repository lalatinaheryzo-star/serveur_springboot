package mg.manao.backend.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import mg.manao.backend.dto.DemandeCooperativeIO;
import mg.manao.backend.exception.ApiException;
import mg.manao.backend.security.CurrentUser;
import mg.manao.backend.security.SecurityUserDetails;
import mg.manao.backend.service.DemandeCooperativeService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * §2 de la spec "Évolution du projet" :
 *  - POST   /demandes-cooperatives          : un utilisateur connecté (VOYAGEUR) dépose sa demande
 *  - GET    /demandes-cooperatives           : ADMIN uniquement, toutes les demandes
 *  - GET    /demandes-cooperatives/me        : l'utilisateur connecté consulte ses propres demandes
 *  - PATCH  /demandes-cooperatives/{id}/approve : ADMIN uniquement
 *  - PATCH  /demandes-cooperatives/{id}/reject  : ADMIN uniquement
 *
 * [À CONFIRMER] Ces routes ne sont pas encore appelées par le frontend :
 * AdminDemandesCooperatives.jsx est aujourd'hui un prototype 100% frontend
 * (voir son en-tête de fichier). Les brancher dessus est une étape séparée.
 */
@RestController
@RequestMapping("/demandes-cooperatives")
@RequiredArgsConstructor
public class DemandeCooperativeController {

    private final DemandeCooperativeService demandeService;

    @GetMapping
    public List<DemandeCooperativeIO.DemandeDTO> findAll() {
        return demandeService.findAll();
    }

    @GetMapping("/me")
    public List<DemandeCooperativeIO.DemandeDTO> findMine() {
        SecurityUserDetails current = CurrentUser.get();
        if (current == null) throw ApiException.unauthorized("Non authentifié.");
        return demandeService.findMine(current.getId());
    }

    @PostMapping
    public ResponseEntity<DemandeCooperativeIO.DemandeDTO> create(@Valid @RequestBody DemandeCooperativeIO.CreateRequest req) {
        SecurityUserDetails current = CurrentUser.get();
        if (current == null) throw ApiException.unauthorized("Non authentifié.");
        return ResponseEntity.status(201).body(demandeService.create(req, current.getId()));
    }

    @PatchMapping("/{id}/approve")
    public DemandeCooperativeIO.DemandeDTO approve(@PathVariable UUID id) {
        return demandeService.approve(id);
    }

    @PatchMapping("/{id}/reject")
    public DemandeCooperativeIO.DemandeDTO reject(@PathVariable UUID id, @RequestBody(required = false) DemandeCooperativeIO.RejectRequest req) {
        return demandeService.reject(id, req);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        demandeService.delete(id);
        return ResponseEntity.noContent().build();
    }
}

