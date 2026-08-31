package mg.manao.backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.OffsetDateTime;

/**
 * Représentation publique d'un utilisateur — LoginAdmin.jsx vérifie
 * `user.role !== "admin"`, donc role doit être en minuscules ("admin" |
 * "voyageur"), pas l'enum Java en majuscules.
 */
@Getter
@Builder
@AllArgsConstructor
public class UtilisateurDTO {
    private String id;
    private String nom;
    private String prenom;
    private String email;
    private String telephone;
    private String role; // "admin" | "voyageur"

    @JsonProperty("date_creation")
    private OffsetDateTime dateCreation;
}
