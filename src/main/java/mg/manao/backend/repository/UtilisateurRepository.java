package mg.manao.backend.repository;

import jakarta.persistence.LockModeType;
import mg.manao.backend.entity.Utilisateur;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface UtilisateurRepository extends JpaRepository<Utilisateur, UUID> {
    Optional<Utilisateur> findByEmailIgnoreCase(String email);
    boolean existsByEmailIgnoreCase(String email);

    /**
     * Même recherche que findByEmailIgnoreCase, avec verrou pessimiste sur la ligne :
     * les tentatives de connexion / de code simultanées pour un même compte sont
     * sérialisées, donc les compteurs d'échecs ne peuvent pas être contournés par
     * des requêtes parallèles. À appeler uniquement dans une transaction.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM Utilisateur u WHERE LOWER(u.email) = LOWER(:email)")
    Optional<Utilisateur> findByEmailForUpdate(@Param("email") String email);
}
