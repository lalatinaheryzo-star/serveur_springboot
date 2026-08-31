package mg.manao.backend.controller;

import lombok.RequiredArgsConstructor;
import mg.manao.backend.dto.UtilisateurDTO;
import mg.manao.backend.dto.UtilisateurRequest;
import mg.manao.backend.service.UtilisateurService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/** ADMIN uniquement — voir SecurityConfig (/utilisateurs/** -> hasRole("ADMIN")). */
@RestController
@RequestMapping("/utilisateurs")
@RequiredArgsConstructor
public class UtilisateurController {

    private final UtilisateurService utilisateurService;

    @GetMapping
    public List<UtilisateurDTO> findAll() {
        return utilisateurService.findAll();
    }

    @GetMapping("/{id}")
    public UtilisateurDTO findById(@PathVariable UUID id) {
        return utilisateurService.findById(id);
    }

    @PostMapping
    public ResponseEntity<UtilisateurDTO> create(@RequestBody UtilisateurRequest req) {
        return ResponseEntity.status(201).body(utilisateurService.create(req));
    }

    @PutMapping("/{id}")
    public UtilisateurDTO update(@PathVariable UUID id, @RequestBody UtilisateurRequest req) {
        return utilisateurService.update(id, req);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        utilisateurService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
