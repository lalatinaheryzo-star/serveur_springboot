package mg.manao.backend.service;

import mg.manao.backend.entity.RappelWhatsApp;
import mg.manao.backend.entity.Reservation;
import mg.manao.backend.repository.RappelWhatsAppRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

/** Groupe 6 : marquage « envoyé » seulement après succès, pas de doublon, trace des tentatives. */
class RappelWhatsAppServiceTest {

    private RappelWhatsAppRepository repository;
    private WhatsAppService whatsApp;
    private RappelWhatsAppService service;
    private Reservation reservation;
    private RappelWhatsApp ligne;

    @BeforeEach
    void setUp() {
        repository = mock(RappelWhatsAppRepository.class);
        whatsApp = mock(WhatsAppService.class);
        when(whatsApp.estActif()).thenReturn(true);
        service = new RappelWhatsAppService(repository, whatsApp,
                new TransactionTemplate(mock(PlatformTransactionManager.class)), 5);
        reservation = Reservation.builder().id(UUID.randomUUID()).build();
        ligne = RappelWhatsApp.builder().reservationId(reservation.getId())
                .statut(RappelWhatsApp.STATUT_EN_COURS).tentatives(1)
                .derniereTentativeAt(OffsetDateTime.now()).build();
        when(repository.saveAndFlush(any(RappelWhatsApp.class))).thenAnswer(i -> i.getArgument(0));
        when(repository.save(any(RappelWhatsApp.class))).thenAnswer(i -> i.getArgument(0));
    }

    private void premierEnvoi() {
        when(repository.findByReservationId(reservation.getId()))
                .thenReturn(Optional.empty())   // lors de la réservation de l'envoi
                .thenReturn(Optional.of(ligne)); // lors du marquage final
    }

    @Test
    void successfulSendIsMarkedSentOnlyAfterEvolutionAccepted() {
        premierEnvoi();
        when(whatsApp.envoyerRappelVoyage(reservation)).thenReturn(
                new WhatsAppService.ResultatEnvoi(WhatsAppService.Statut.ENVOYE, "HTTP 201"));

        assertTrue(service.envoyerSiNecessaire(reservation));

        assertEquals(RappelWhatsApp.STATUT_ENVOYE, ligne.getStatut());
        assertNotNull(ligne.getEnvoyeAt());
    }

    @Test
    void failedSendIsNeverMarkedSent() {
        premierEnvoi();
        when(whatsApp.envoyerRappelVoyage(reservation)).thenReturn(
                new WhatsAppService.ResultatEnvoi(WhatsAppService.Statut.ECHEC, "HTTP 500"));

        assertFalse(service.envoyerSiNecessaire(reservation));

        assertEquals(RappelWhatsApp.STATUT_ECHEC, ligne.getStatut());
        assertNull(ligne.getEnvoyeAt());
        assertEquals("HTTP 500", ligne.getDerniereErreur());
    }

    @Test
    void unexpectedExceptionIsRecordedAsFailure() {
        premierEnvoi();
        when(whatsApp.envoyerRappelVoyage(reservation)).thenThrow(new IllegalStateException("boom"));

        assertFalse(service.envoyerSiNecessaire(reservation));
        assertEquals(RappelWhatsApp.STATUT_ECHEC, ligne.getStatut());
    }

    @Test
    void invalidPhoneIsNotRetried() {
        premierEnvoi();
        when(whatsApp.envoyerRappelVoyage(reservation)).thenReturn(
                new WhatsAppService.ResultatEnvoi(WhatsAppService.Statut.NUMERO_INVALIDE, "numéro absent ou invalide"));

        service.envoyerSiNecessaire(reservation);

        assertEquals(RappelWhatsAppService.MAX_TENTATIVES, ligne.getTentatives());
    }

    @Test
    void alreadySentReminderIsNeverSentTwice() {
        ligne.setStatut(RappelWhatsApp.STATUT_ENVOYE);
        when(repository.findByReservationId(reservation.getId())).thenReturn(Optional.of(ligne));
        when(repository.reprendreSiEchec(any(), any(), any(), anyInt())).thenReturn(0);

        assertFalse(service.envoyerSiNecessaire(reservation));
        verify(whatsApp, never()).envoyerRappelVoyage(any());
    }

    @Test
    void stuckInProgressRowIsNotReplayed() {
        when(repository.findByReservationId(reservation.getId())).thenReturn(Optional.of(ligne));
        when(repository.reprendreSiEchec(any(), any(), any(), anyInt())).thenReturn(0);

        assertFalse(service.envoyerSiNecessaire(reservation));
        verify(whatsApp, never()).envoyerRappelVoyage(any());
    }

    @Test
    void failedRowIsRetriedWhenTheClaimSucceeds() {
        ligne.setStatut(RappelWhatsApp.STATUT_ECHEC);
        when(repository.findByReservationId(reservation.getId())).thenReturn(Optional.of(ligne));
        when(repository.reprendreSiEchec(any(), any(), any(), anyInt())).thenReturn(1);
        when(whatsApp.envoyerRappelVoyage(reservation)).thenReturn(
                new WhatsAppService.ResultatEnvoi(WhatsAppService.Statut.ENVOYE, "HTTP 201"));

        assertTrue(service.envoyerSiNecessaire(reservation));
        assertEquals(RappelWhatsApp.STATUT_ENVOYE, ligne.getStatut());
    }

    @Test
    void concurrentInstanceLosingTheInsertRaceSendsNothing() {
        when(repository.findByReservationId(reservation.getId())).thenReturn(Optional.empty());
        when(repository.saveAndFlush(any(RappelWhatsApp.class))).thenThrow(new DataIntegrityViolationException("uq"));

        assertFalse(service.envoyerSiNecessaire(reservation));
        verify(whatsApp, never()).envoyerRappelVoyage(any());
    }

    @Test
    void nothingHappensWhenEvolutionIsNotConfigured() {
        when(whatsApp.estActif()).thenReturn(false);
        assertFalse(service.envoyerSiNecessaire(reservation));
        verifyNoInteractions(repository);
    }
}
