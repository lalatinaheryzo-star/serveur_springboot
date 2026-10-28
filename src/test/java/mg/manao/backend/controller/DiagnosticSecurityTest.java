package mg.manao.backend.controller;

import mg.manao.backend.config.SecurityConfig;
import mg.manao.backend.security.CustomUserDetailsService;
import mg.manao.backend.security.JwtAuthFilter;
import mg.manao.backend.security.JwtService;
import mg.manao.backend.service.EmailService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** R7 : seul l'ADMIN peut atteindre /diagnostic/** (vérifie la vraie configuration Spring Security). */
@WebMvcTest(EmailDiagnosticController.class)
@Import({SecurityConfig.class, JwtAuthFilter.class})
class DiagnosticSecurityTest {

    @Autowired private MockMvc mockMvc;
    @MockBean private EmailService emailService;
    @MockBean private CustomUserDetailsService customUserDetailsService;
    @MockBean private JwtService jwtService;

    @Test
    @WithMockUser(roles = "VOYAGEUR")
    void voyageurCannotSendDiagnosticEmails() throws Exception {
        mockMvc.perform(post("/diagnostic/email/test").param("destinataire", "victime@example.com"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/diagnostic/email/connexion")).andExpect(status().isForbidden());
        verify(emailService, never()).envoyerEmailTest(anyString());
        verify(emailService, never()).diagnostiquerConnexion();
    }

    @Test
    @WithMockUser(roles = "PRESIDENT")
    void presidentCannotSendDiagnosticEmails() throws Exception {
        mockMvc.perform(post("/diagnostic/email/test").param("destinataire", "victime@example.com"))
                .andExpect(status().isForbidden());
        verify(emailService, never()).envoyerEmailTest(anyString());
    }

    @Test
    void anonymousCannotReachDiagnostic() throws Exception {
        mockMvc.perform(get("/diagnostic/email/connexion")).andExpect(status().isForbidden());
        verify(emailService, never()).diagnostiquerConnexion();
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminKeepsAccessToDiagnostic() throws Exception {
        mockMvc.perform(get("/diagnostic/email/connexion")).andExpect(status().isOk());
        mockMvc.perform(post("/diagnostic/email/test").param("destinataire", "admin@example.com"))
                .andExpect(status().isOk());
        verify(emailService).diagnostiquerConnexion();
        verify(emailService).envoyerEmailTest("admin@example.com");
    }
}
