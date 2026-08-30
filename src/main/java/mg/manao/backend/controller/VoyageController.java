package mg.manao.backend.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import mg.manao.backend.dto.VoyageDTO;
import mg.manao.backend.dto.VoyageRequest;
import mg.manao.backend.entity.Cooperative;
import mg.manao.backend.exception.ApiException;
import mg.manao.backend.security.CurrentUser;
import mg.manao.backend.security.SecurityUserDetails;
import mg.manao.backend.service.CooperativeService;
import mg.manao.backend.service.VoyageService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * GET public (parcours des voyages avant connexion) — POST/PUT/DELETE réservés
 * au Président de la coopérative propriétaire du voyage (§4 de la spec :
 * l'ADMIN ne crée/modifie plus de voyage, voir SecurityConfig).
 */
@RestController
@RequestMapping("/voyages")
@RequiredArgsConstructor
public class VoyageController {

    private final VoyageService voyageService;
    private final CooperativeService cooperativeService;

    @GetMapping
    public List<VoyageDTO> findAll() {
        return voyageService.findAll();
    }

    @GetMapping("/{id}")
    public VoyageDTO findById(@PathVariable UUID id) {
        return voyageService.findById(id);
    }

    @PostMapping
    public ResponseEntity<VoyageDTO> create(@Valid @RequestBody VoyageRequest req) {
        Cooperative coop = myCooperative();
        return ResponseEntity.status(201).body(voyageService.create(req, coop));
    }

    @PutMapping("/{id}")
    public VoyageDTO update(@PathVariable UUID id, @RequestBody VoyageRequest req) {
        Cooperative coop = myCooperative();
        return voyageService.update(id, req, coop);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        Cooperative coop = myCooperative();
        voyageService.delete(id, coop);
        return ResponseEntity.noContent().build();
    }

    private Cooperative myCooperative() {
        SecurityUserDetails current = CurrentUser.get();
        if (current == null) throw ApiException.unauthorized("Non authentifié.");
        return cooperativeService.getEntityForPresident(current.getId());
    }
}
