package mg.manao.backend.service;

import mg.manao.backend.entity.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** Groupe 6 : aucun appel réseau réel (RestTemplate simulé). */
class WhatsAppServiceTest {

    private WhatsAppService service;
    private RestTemplate rest;
    private Reservation reservation;

    @BeforeEach
    void setUp() {
        service = new WhatsAppService();
        rest = mock(RestTemplate.class);
        service.restTemplate = rest;
        ReflectionTestUtils.setField(service, "enabled", true);
        ReflectionTestUtils.setField(service, "apiUrl", "http://evolution.invalid");
        ReflectionTestUtils.setField(service, "apiKey", "cle-fictive");
        ReflectionTestUtils.setField(service, "instance", "inst");
        ReflectionTestUtils.setField(service, "defaultCountryCode", "261");
        Voyage v = Voyage.builder().villeArrivee("Toamasina").dateDepart(LocalDate.now().plusDays(1))
                .heureDepart(LocalTime.NOON).build();
        Utilisateur u = Utilisateur.builder().id(UUID.randomUUID()).prenom("Ada").telephone("034 12 345 67").build();
        reservation = Reservation.builder().id(UUID.randomUUID()).voyage(v).utilisateur(u).build();
    }

    @Test
    void notConfiguredNeverCallsTheNetwork() {
        ReflectionTestUtils.setField(service, "enabled", false);
        assertEquals(WhatsAppService.Statut.NON_CONFIGURE, service.envoyerRappelVoyage(reservation).statut());
        verifyNoInteractions(rest);
    }

    @Test
    void invalidPhoneIsReportedWithoutCall() {
        reservation.getUtilisateur().setTelephone("abc");
        assertEquals(WhatsAppService.Statut.NUMERO_INVALIDE, service.envoyerRappelVoyage(reservation).statut());
        verifyNoInteractions(rest);
    }

    @Test
    void http2xxIsSent() {
        when(rest.exchange(anyString(), eq(HttpMethod.POST), any(), eq(String.class)))
                .thenReturn(ResponseEntity.status(HttpStatus.CREATED).body("{}"));
        assertTrue(service.envoyerRappelVoyage(reservation).estEnvoye());
    }

    @Test
    void http5xxIsAFailureWithoutLeakingDetails() {
        when(rest.exchange(anyString(), eq(HttpMethod.POST), any(), eq(String.class)))
                .thenThrow(new HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR, "corps secret"));
        WhatsAppService.ResultatEnvoi r = service.envoyerRappelVoyage(reservation);
        assertFalse(r.estEnvoye());
        assertEquals("HTTP 500", r.detail());
    }

    @Test
    void timeoutIsAFailureAndNeverThrows() {
        when(rest.exchange(anyString(), eq(HttpMethod.POST), any(), eq(String.class)))
                .thenThrow(new ResourceAccessException("http://evolution.invalid?apikey=xxx"));
        WhatsAppService.ResultatEnvoi r = service.envoyerRappelVoyage(reservation);
        assertEquals(WhatsAppService.Statut.ECHEC, r.statut());
        assertFalse(r.detail().contains("apikey"));
    }

    @Test
    void phoneNumbersAreMaskedForLogs() {
        assertEquals("***67", WhatsAppService.masquer("261341234567"));
    }

    private static String anyString() { return org.mockito.ArgumentMatchers.anyString(); }
}
