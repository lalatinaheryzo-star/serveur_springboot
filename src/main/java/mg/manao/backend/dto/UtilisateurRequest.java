package mg.manao.backend.dto;

import jakarta.validation.constraints.Email;
import lombok.Getter;
import lombok.Setter;

/**
 * Payload de src/pages/Utilisateurs.jsx (admin) :
 *   { nom, prenom, email, telephone, password }
 * `password` est optionnel en modification (laisser vide = ne pas changer).
 */
@Getter
@Setter
public class UtilisateurRequest {
    private String nom;
    private String prenom;

    @Email
    private String email;

    private String telephone;
    private String password;
}
