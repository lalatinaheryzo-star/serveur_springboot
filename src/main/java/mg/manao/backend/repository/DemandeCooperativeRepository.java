package mg.manao.backend.repository;

import mg.manao.backend.entity.DemandeCooperative;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DemandeCooperativeRepository extends JpaRepository<DemandeCooperative, UUID> {
    List<DemandeCooperative> findAllByOrderByDateCreationDesc();
    List<DemandeCooperative> findByUtilisateurIdOrderByDateCreationDesc(UUID utilisateurId);
    Optional<DemandeCooperative> findFirstByUtilisateurIdAndStatut(UUID utilisateurId, DemandeCooperative.Statut statut);

    /** Utilisé pour empêcher un même email de déposer plusieurs demandes actives (PENDING ou déjà APPROUVEE). */
    Optional<DemandeCooperative> findFirstByEmailIgnoreCaseAndStatutIn(String email, List<DemandeCooperative.Statut> statuts);
}
