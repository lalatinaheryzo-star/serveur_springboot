package mg.manao.backend.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import mg.manao.backend.config.SecurityConfig;
import mg.manao.backend.security.CustomUserDetailsService;
import mg.manao.backend.security.JwtAuthFilter;
import mg.manao.backend.security.JwtService;
import mg.manao.backend.service.RecuService;
import mg.manao.backend.service.ReservationService;
import org.springframework.security.test.context.support.WithMockUser;
import org.mockito.Mockito;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Groupe 5 : le check-in n'est plus public (POST /recus/verify/{token}/checkin sans jeton -> 401/403). */
@WebMvcTest(RecuController.class)
@Import({SecurityConfig.class, JwtAuthFilter.class})
class CheckinSecurityTest {

    @Autowired MockMvc mvc;
    @MockBean RecuService recuService;
    @MockBean ReservationService reservationService;
    @MockBean JwtService jwtService;
    @MockBean CustomUserDetailsService userDetailsService;

    @Test
    void anonymousCheckinIsRejected() throws Exception {
        mvc.perform(post("/recus/verify/abc/checkin")).andExpect(status().is4xxClientError());
        Mockito.verifyNoInteractions(recuService);
    }

    @Test
    @WithMockUser(roles = "VOYAGEUR")
    void voyageurSessionCannotBoardAnyone() throws Exception {
        // Authentifié mais sans identité applicative exploitable : jamais d'appel au service d'embarquement.
        mvc.perform(post("/recus/verify/abc/checkin")).andExpect(status().is4xxClientError());
        Mockito.verify(recuService, Mockito.never()).checkin(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());
    }
}
