package mg.manao.backend.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import mg.manao.backend.dto.CooperativeDTO;
import mg.manao.backend.dto.CooperativeRequest;
import mg.manao.backend.exception.ApiException;
import mg.manao.backend.repository.UtilisateurRepository;
import mg.manao.backend.security.CurrentUser;
import mg.manao.backend.security.SecurityUserDetails;
import mg.manao.backend.service.CooperativeService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * GET public (voir SecurityConfig) — nécessaire pour VoyagesClient.jsx (voyageur non
 * connecté peut parcourir les voyages) et pour la modale "Contacter la coopérative".
 * POST réservé au Président déjà approuvé par l'Admin (rôle PRESIDENT) : il crée
 * SA PROPRE coopérative — l'admin ne la crée jamais à sa place (voir
 * DemandeCooperativeService.approve(), qui autorise sans créer). Un seul appel
 * possible par Président (règle 1:1, vérifiée dans CooperativeService.create()).
 * PUT réservé au Président propriétaire (§7 : l'ADMIN ne modifie plus une
 * coopérative, il la consulte seulement — DELETE reste une action de supervision ADMIN).
 */
@RestController
@RequestMapping("/cooperatives")
@RequiredArgsConstructor
public class CooperativeController {

    private final CooperativeService cooperativeService;
    private final UtilisateurRepository utilisateurRepository;

    @GetMapping
    public List<CooperativeDTO> findAll() {
        return cooperativeService.findAll();
    }

    @GetMapping("/{id}")
    public CooperativeDTO findById(@PathVariable UUID id) {
        return CooperativeService.toDto(cooperativeService.getEntity(id));
    }

    /** Le Président connecté consulte sa propre coopérative (espace "Ma Coopérative", §6). */
    @GetMapping("/me")
    public CooperativeDTO findMine() {
        SecurityUserDetails current = requirePresident();
        return cooperativeService.findMine(current.getId());
    }

    /** Le Président, une fois approuvé par l'Admin, crée lui-même sa coopérative. */
    @PostMapping
    public ResponseEntity<CooperativeDTO> create(@Valid @RequestBody CooperativeRequest req) {
        SecurityUserDetails current = requirePresident();
        var president = utilisateurRepository.findById(current.getId())
                .orElseThrow(() -> ApiException.unauthorized("Compte introuvable."));
        return ResponseEntity.status(201).body(cooperativeService.create(req, president));
    }

    @PutMapping("/{id}")
    public CooperativeDTO update(@PathVariable UUID id, @RequestBody CooperativeRequest req) {
        SecurityUserDetails current = requirePresident();
        return cooperativeService.update(id, req, current.getId());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        cooperativeService.delete(id);
        return ResponseEntity.noContent().build();
    }

    private SecurityUserDetails requirePresident() {
        SecurityUserDetails current = CurrentUser.get();
        if (current == null) throw ApiException.unauthorized("Non authentifié.");
        if (current.getRole() != mg.manao.backend.entity.Utilisateur.Role.PRESIDENT) {
            throw ApiException.forbidden("Réservé au Président de la coopérative.");
        }
        return current;
    }
}
