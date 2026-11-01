package mg.manao.backend.controller;

import mg.manao.backend.config.SecurityConfig;
import mg.manao.backend.security.CustomUserDetailsService;
import mg.manao.backend.security.JwtAuthFilter;
import mg.manao.backend.security.JwtService;
import mg.manao.backend.service.AuthService;
import mg.manao.backend.service.UtilisateurService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, JwtAuthFilter.class})
class ForgotPasswordSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AuthService authService;
    @MockBean
    private UtilisateurService utilisateurService;
    @MockBean
    private CustomUserDetailsService customUserDetailsService;
    @MockBean
    private JwtService jwtService;

    @Test
    void protectedAuthRouteRemainsProtected() throws Exception {
        mockMvc.perform(get("/auth/me"))
                .andExpect(status().isForbidden());
    }

    @Test
    void removedForgotPasswordEndpointsAreNotPublic() throws Exception {
        // La gestion du mot de passe oublié a été migrée vers le serveur Node.js.
        // Ces endpoints ne sont plus exposés publiquement sur le Spring Boot.
        mockMvc.perform(post("/auth/forgot-password"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/auth/password/nouveau-code"))
                .andExpect(status().isForbidden());
    }
}
