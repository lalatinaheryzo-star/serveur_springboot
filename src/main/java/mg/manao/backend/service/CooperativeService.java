package mg.manao.backend.service;

import lombok.RequiredArgsConstructor;
import mg.manao.backend.dto.CooperativeDTO;
import mg.manao.backend.dto.CooperativeRequest;
import mg.manao.backend.entity.Cooperative;
import mg.manao.backend.entity.Utilisateur;
import mg.manao.backend.exception.ApiException;
import mg.manao.backend.repository.CooperativeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CooperativeService {

    private final CooperativeRepository cooperativeRepository;

    public static CooperativeDTO toDto(Cooperative c) {
        return CooperativeDTO.builder()
                .id(c.getId().toString())
                .nom(c.getNom())
                .adresse(c.getAdresse())
                .telephone(c.getTelephone())
                .presidentId(c.getPresident() != null ? c.getPresident().getId().toString() : null)
                .rappelActif(c.isRappelActif())
                .rappelDelaiMinutes(c.getRappelDelaiMinutes())
                .build();
    }

    public List<CooperativeDTO> findAll() {
        return cooperativeRepository.findAll().stream().map(CooperativeService::toDto).toList();
    }

    public Cooperative getEntity(UUID id) {
        return cooperativeRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Coopérative introuvable."));
    }

    /** Utilisé partout où une action Président doit être limitée à SA coopérative (voyages, réservations, paiements...). */
    public Cooperative getEntityForPresident(UUID presidentId) {
        return cooperativeRepository.findByPresidentId(presidentId)
                .orElseThrow(() -> ApiException.notFound("Aucune coopérative associée à ce compte pour l'instant."));
    }

    public boolean hasCooperative(UUID presidentId) {
        return cooperativeRepository.findByPresidentId(presidentId).isPresent();
    }

    public CooperativeDTO findMine(UUID presidentId) {
        return toDto(getEntityForPresident(presidentId));
    }

    /**
     * Création de SA coopérative par un Président déjà approuvé (§ workflow
     * "objectif 2" : l'admin autorise, mais ne crée pas la coopérative à la
     * place du Président — c'est une action distincte et volontaire du
     * Président). Un seul appel possible par Président (règle 1:1, vérifiée
     * ici en plus de la contrainte UNIQUE en base).
     */
    @Transactional
    public CooperativeDTO create(CooperativeRequest req, Utilisateur president) {
        if (cooperativeRepository.findByPresidentId(president.getId()).isPresent()) {
            throw ApiException.conflict("Vous avez déjà créé votre coopérative.");
        }
        Cooperative c = Cooperative.builder()
                .nom(req.getNom().trim())
                .adresse(blankToNull(req.getAdresse()))
                .telephone(blankToNull(req.getTelephone()))
                .president(president)
                .build();
        return toDto(cooperativeRepository.save(c));
    }

    /**
     * Modification des informations de la coopérative : réservée au Président
     * propriétaire (vérifié ici, pas seulement dans SecurityConfig — §7 de la
     * spec). L'ADMIN ne modifie plus une coopérative, il ne fait que la
     * consulter (§1) ; ce endpoint n'est donc plus appelable par un ADMIN
     * (voir SecurityConfig : PUT /cooperatives/** -> hasRole("PRESIDENT")).
     */
    @Transactional
    public CooperativeDTO update(UUID id, CooperativeRequest req, UUID presidentId) {
        Cooperative c = getEntity(id);
        if (c.getPresident() == null || !c.getPresident().getId().equals(presidentId)) {
            throw ApiException.forbidden("Vous ne pouvez modifier que votre propre coopérative.");
        }
        if (req.getNom() != null && !req.getNom().isBlank()) c.setNom(req.getNom().trim());
        c.setAdresse(blankToNull(req.getAdresse()));
        c.setTelephone(blankToNull(req.getTelephone()));
        // Rappel de voyage : Boolean/Integer (types objet) -> null signifie
        // "non fourni dans cette requête", donc on ne touche pas au champ
        // existant (contrairement à adresse/telephone ci-dessus qui sont
        // toujours écrasés — comportement existant volontairement inchangé).
        if (req.getRappelActif() != null) c.setRappelActif(req.getRappelActif());
        if (req.getRappelDelaiMinutes() != null) {
            int delai = req.getRappelDelaiMinutes();
            if (delai < 1 || delai > 1440) {
                throw ApiException.badRequest("Le délai de rappel doit être compris entre 1 et 1440 minutes.");
            }
            c.setRappelDelaiMinutes(delai);
        }
        return toDto(cooperativeRepository.save(c));
    }

    @Transactional
    public void delete(UUID id) {
        if (!cooperativeRepository.existsById(id)) {
            throw ApiException.notFound("Coopérative introuvable.");
        }
        try {
            cooperativeRepository.deleteById(id);
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            throw ApiException.conflict("Impossible de supprimer : des voyages sont encore rattachés à cette coopérative.");
        }
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }
}
