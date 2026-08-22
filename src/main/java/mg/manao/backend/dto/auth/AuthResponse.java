package mg.manao.backend.dto.auth;

import lombok.AllArgsConstructor;
import lombok.Getter;
import mg.manao.backend.dto.UtilisateurDTO;

/**
 * services.js: `const data = await apiClient.post("/auth/login", ...); setToken(data.token); return data.user;`
 * -> { token, user }
 */
@Getter
@AllArgsConstructor
public class AuthResponse {
    private String token;
    private UtilisateurDTO user;
}
