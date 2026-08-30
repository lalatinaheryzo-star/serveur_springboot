package mg.manao.backend.service;

import lombok.RequiredArgsConstructor;
import mg.manao.backend.dto.DemandeCooperativeIO;
import mg.manao.backend.entity.DemandeCooperative;
import mg.manao.backend.entity.Utilisateur;
import mg.manao.backend.exception.ApiException;
import mg.manao.backend.repository.DemandeCooperativeRepository;
import mg.manao.backend.repository.UtilisateurRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Workflow "Demande de création de coopérative" (§2 de la spec).
 * Étapes : VOYAGEUR crée une demande (PENDING) -> ADMIN approuve ou rejette.
 * En cas d'approbation : la coopérative est créée, le compte devient
 * PRESIDENT et est rattaché 1:1 à cette coopérative (§3).
 */
@Service
@RequiredArgsConstructor
public class DemandeCooperativeService {

    private final DemandeCooperativeRepository demandeRepository;
    private final UtilisateurRepository utilisateurRepository;

    public static DemandeCooperativeIO.DemandeDTO toDto(DemandeCooperative d) {
        return DemandeCooperativeIO.DemandeDTO.builder()
                .id(d.getId().toString())
                .utilisateurId(d.getUtilisateur() != null ? d.getUtilisateur().getId().toString() : null)
                .nomPresident(d.getNomPresident())
                .telephone(d.getTelephone())
                .email(d.getEmail())
                .cin(d.getCin())
                .nomCooperative(d.getNomCooperative())
                .ville(d.getVille())
                .adresse(d.getAdresse())
                .message(d.getMessage())
                .statut(d.getStatut().name())
                .motifRejet(d.getMotifRejet())
                .cooperativeId(d.getCooperative() != null ? d.getCooperative().getId().toString() : null)
                .dateCreation(d.getDateCreation())
                .dateTraitement(d.getDateTraitement())
                .build();
    }

    public List<DemandeCooperativeIO.DemandeDTO> findAll() {
        return demandeRepository.findAllByOrderByDateCreationDesc().stream().map(DemandeCooperativeService::toDto).toList();
    }

    public List<DemandeCooperativeIO.DemandeDTO> findMine(UUID utilisateurId) {
        return demandeRepository.findByUtilisateurIdOrderByDateCreationDesc(utilisateurId)
                .stream().map(DemandeCooperativeService::toDto).toList();
    }

    public DemandeCooperative getEntity(UUID id) {
        return demandeRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Demande introuvable."));
    }

    @Transactional
    public DemandeCooperativeIO.DemandeDTO create(DemandeCooperativeIO.CreateRequest req, UUID utilisateurId) {
        Utilisateur u = utilisateurRepository.findById(utilisateurId)
                .orElseThrow(() -> ApiException.notFound("Utilisateur introuvable."));

        if (u.getRole() != Utilisateur.Role.VOYAGEUR) {
            throw ApiException.badRequest("Seul un compte Voyageur peut demander la création d'une coopérative.");
        }
        if (demandeRepository.findFirstByUtilisateurIdAndStatut(utilisateurId, DemandeCooperative.Statut.PENDING).isPresent()) {
            throw ApiException.conflict("Une demande est déjà en attente pour ce compte.");
        }
        // Empêche un même email de déposer une 2e demande tant que la précédente
        // est en attente ou a déjà été approuvée (mais autorise un nouvel essai
        // après un refus, conformément au message affiché à l'utilisateur).
        String email = req.getEmail().trim();
        if (demandeRepository.findFirstByEmailIgnoreCaseAndStatutIn(
                email, List.of(DemandeCooperative.Statut.PENDING, DemandeCooperative.Statut.APPROUVEE)).isPresent()) {
            throw ApiException.conflict("Une demande existe déjà pour cet email (en attente ou déjà approuvée).");
        }

        DemandeCooperative d = DemandeCooperative.builder()
                .utilisateur(u)
                .nomPresident(req.getNomPresident().trim())
                .telephone(req.getTelephone().trim())
                .email(req.getEmail().trim())
                .cin(blankToNull(req.getCin()))
                .nomCooperative(req.getNomCooperative().trim())
                .ville(blankToNull(req.getVille()))
                .adresse(blankToNull(req.getAdresse()))
                .message(blankToNull(req.getMessage()))
                .statut(DemandeCooperative.Statut.PENDING)
                .build();

        return toDto(demandeRepository.save(d));
    }

    /**
     * Approbation (§ nouveau workflow "objectif 2") : autorise le candidat à
     * poursuivre (rôle -> PRESIDENT), mais NE crée PAS sa coopérative à sa
     * place. Le Président crée lui-même sa coopérative ensuite, via
     * POST /cooperatives (voir CooperativeService.create(), réservé aux
     * PRESIDENT, avec la règle 1 Président = 1 coopérative).
     */
    @Transactional
    public DemandeCooperativeIO.DemandeDTO approve(UUID id) {
        DemandeCooperative d = getEntity(id);
        if (d.getStatut() != DemandeCooperative.Statut.PENDING) {
            throw ApiException.conflict("Cette demande a déjà été traitée.");
        }

        Utilisateur u = d.getUtilisateur();
        if (u.getRole() == Utilisateur.Role.PRESIDENT) {
            throw ApiException.conflict("Ce compte est déjà autorisé en tant que Président.");
        }

        u.setRole(Utilisateur.Role.PRESIDENT);
        utilisateurRepository.save(u);

        d.setStatut(DemandeCooperative.Statut.APPROUVEE);
        d.setDateTraitement(OffsetDateTime.now());
        return toDto(demandeRepository.save(d));
    }

    @Transactional
    public DemandeCooperativeIO.DemandeDTO reject(UUID id, DemandeCooperativeIO.RejectRequest req) {
        DemandeCooperative d = getEntity(id);
        if (d.getStatut() != DemandeCooperative.Statut.PENDING) {
            throw ApiException.conflict("Cette demande a déjà été traitée.");
        }
        d.setStatut(DemandeCooperative.Statut.REJETEE);
        d.setMotifRejet(req != null ? blankToNull(req.getMotif()) : null);
        d.setDateTraitement(OffsetDateTime.now());
        return toDto(demandeRepository.save(d));
    }

  @Transactional
    public void delete(UUID id) {
        DemandeCooperative d = getEntity(id);
        demandeRepository.delete(d);
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }
}
