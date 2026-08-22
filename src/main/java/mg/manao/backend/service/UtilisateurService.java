package mg.manao.backend.service;

import lombok.RequiredArgsConstructor;
import mg.manao.backend.dto.UtilisateurDTO;
import mg.manao.backend.dto.UtilisateurRequest;
import mg.manao.backend.entity.Utilisateur;
import mg.manao.backend.exception.ApiException;
import mg.manao.backend.repository.UtilisateurRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UtilisateurService {

    private final UtilisateurRepository utilisateurRepository;
    private final PasswordEncoder passwordEncoder;

    public static UtilisateurDTO toDto(Utilisateur u) {
        return UtilisateurDTO.builder()
                .id(u.getId().toString())
                .nom(u.getNom())
                .prenom(u.getPrenom())
                .email(u.getEmail())
                .telephone(u.getTelephone())
                .role(u.getRole().name().toLowerCase())
                .dateCreation(u.getDateCreation())
                .build();
    }

    public List<UtilisateurDTO> findAll() {
        return utilisateurRepository.findAll().stream()
                .map(UtilisateurService::toDto)
                .toList();
    }

    public Utilisateur getEntity(UUID id) {
        return utilisateurRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Utilisateur introuvable."));
    }

    public UtilisateurDTO findById(UUID id) {
        return toDto(getEntity(id));
    }

    @Transactional
    public UtilisateurDTO create(UtilisateurRequest req) {
        if (req.getEmail() == null || req.getEmail().isBlank()) {
            throw ApiException.badRequest("L'email est obligatoire.");
        }
        if (req.getPassword() == null || req.getPassword().length() < 6) {
            throw ApiException.badRequest("Le mot de passe doit contenir au moins 6 caractères.");
        }
        if (utilisateurRepository.existsByEmailIgnoreCase(req.getEmail())) {
            throw ApiException.conflict("Cet email est déjà utilisé.");
        }
        Utilisateur u = Utilisateur.builder()
                .nom(req.getNom())
                .prenom(req.getPrenom())
                .email(req.getEmail().trim().toLowerCase())
                .telephone(req.getTelephone())
                .motDePasse(passwordEncoder.encode(req.getPassword()))
                .role(Utilisateur.Role.VOYAGEUR)
                .build();
        return toDto(utilisateurRepository.save(u));
    }

    @Transactional
    public UtilisateurDTO update(UUID id, UtilisateurRequest req) {
        Utilisateur u = getEntity(id);
        if (req.getNom() != null) u.setNom(req.getNom());
        if (req.getPrenom() != null) u.setPrenom(req.getPrenom());
        if (req.getEmail() != null && !req.getEmail().isBlank()) {
            String newEmail = req.getEmail().trim().toLowerCase();
            if (!newEmail.equalsIgnoreCase(u.getEmail()) && utilisateurRepository.existsByEmailIgnoreCase(newEmail)) {
                throw ApiException.conflict("Cet email est déjà utilisé.");
            }
            u.setEmail(newEmail);
        }
        u.setTelephone(req.getTelephone());
        if (req.getPassword() != null && !req.getPassword().isBlank()) {
            if (req.getPassword().length() < 6) {
                throw ApiException.badRequest("Le mot de passe doit contenir au moins 6 caractères.");
            }
            u.setMotDePasse(passwordEncoder.encode(req.getPassword()));
        }
        return toDto(utilisateurRepository.save(u));
    }

    @Transactional
    public void delete(UUID id) {
        if (!utilisateurRepository.existsById(id)) {
            throw ApiException.notFound("Utilisateur introuvable.");
        }
        utilisateurRepository.deleteById(id);
    }
}
