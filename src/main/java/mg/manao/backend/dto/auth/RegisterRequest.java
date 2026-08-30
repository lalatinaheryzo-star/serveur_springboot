package mg.manao.backend.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RegisterRequest {
    @NotBlank
    private String nom;

    @NotBlank
    private String prenom;

    @NotBlank @Email
    private String email;

    @NotBlank @Size(min = 6, message = "6 caractères minimum")
    private String password;

    private String telephone;
}
