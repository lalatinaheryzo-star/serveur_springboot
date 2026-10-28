package mg.manao.backend.service;

import mg.manao.backend.dto.RecuDTO;
import mg.manao.backend.entity.*;
import mg.manao.backend.exception.ApiException;
import mg.manao.backend.repository.PaiementRepository;
import mg.manao.backend.repository.RecuRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Groupes 1 et 5 : reçu conditionnel, embarquement autorisé, vue publique minimale. */
class RecuCheckinTest {

    private RecuRepository recuRepository;
    private RecuService service;

    private final UUID presidentId = UUID.randomUUID();
    private Reservation reservation;
    private Paiement paiement;
    private Recu recu;

    @BeforeEach
    void setUp() {
        recuRepository = mock(RecuRepository.class);
        service = new RecuService(recuRepository, mock(PaiementRepository.class), mock(PdfService.class));
        ReflectionTestUtils.setField(service, "frontendUrl", "https://front.example");

        Utilisateur president = Utilisateur.builder().id(presidentId).build();
        Cooperative coop = Cooperative.builder().id(UUID.randomUUID()).nom("Coop").president(president).build();
        Voyage voyage = Voyage.builder().id(UUID.randomUUID()).cooperative(coop).villeDepart("A").villeArrivee("B").build();
        Utilisateur voyageur = Utilisateur.builder().id(UUID.randomUUID()).nom("Rakoto").prenom("Ada")
                .telephone("034 12 345 67").build();
        reservation = Reservation.builder().id(UUID.randomUUID()).voyage(voyage).utilisateur(voyageur)
                .statut(Reservation.STATUT_VALIDEE).numeroPlace(3).build();
        paiement = Paiement.builder().id(UUID.randomUUID()).reservation(reservation)
                .statut(Paiement.STATUT_REUSSI).modePaiement("Espèces").build();
        recu = Recu.builder().id(UUID.randomUUID()).paiement(paiement).numeroRecu("REC-2026-000001")
                .token("tok").statut("Confirmée").build();
        when(recuRepository.findByTokenForUpdate("tok")).thenReturn(Optional.of(recu));
        when(recuRepository.findByPaiementId(paiement.getId())).thenReturn(Optional.empty());
        when(recuRepository.save(any(Recu.class))).thenAnswer(i -> i.getArgument(0));
    }

    // ── Groupe 1 : génération conditionnelle ──
    @Test
    void noReceiptWhilePaymentIsPending() {
        paiement.setStatut(Paiement.STATUT_EN_ATTENTE);
        assertNull(service.ensureRecuForPaiement(paiement));
        verify(recuRepository, never()).save(any());
    }

    @Test
    void noReceiptWhileReservationIsNotValidated() {
        reservation.setStatut(Reservation.STATUT_EN_ATTENTE);
        assertNull(service.ensureRecuForPaiement(paiement));
        verify(recuRepository, never()).save(any());
    }

    @Test
    void receiptIsGeneratedWhenPaidAndValidated() {
        assertNotNull(service.ensureRecuForPaiement(paiement));
        verify(recuRepository).save(any(Recu.class));
    }

    @Test
    void receiptGenerationIsIdempotent() {
        when(recuRepository.findByPaiementId(paiement.getId())).thenReturn(Optional.of(recu));
        assertSame(recu, service.ensureRecuForPaiement(paiement));
        verify(recuRepository, never()).save(any());
    }

    // ── Groupe 5 : embarquement ──
    @Test
    void presidentOfTheCooperativeCanCheckIn() {
        service.checkin("tok", presidentId, Utilisateur.Role.PRESIDENT, "Pres Ident");
        assertNotNull(recu.getCheckedAt());
        assertEquals("Pres Ident", recu.getCheckedBy());
        assertEquals("Embarquée", recu.getStatut());
    }

    @Test
    void adminCanCheckIn() {
        service.checkin("tok", UUID.randomUUID(), Utilisateur.Role.ADMIN, "Admin");
        assertNotNull(recu.getCheckedAt());
    }

    @Test
    void voyageurCannotCheckIn() {
        ApiException e = assertThrows(ApiException.class,
                () -> service.checkin("tok", UUID.randomUUID(), Utilisateur.Role.VOYAGEUR, "x"));
        assertEquals(HttpStatus.FORBIDDEN, e.getStatus());
        assertNull(recu.getCheckedAt());
    }

    @Test
    void presidentOfAnotherCooperativeCannotCheckIn() {
        assertEquals(HttpStatus.FORBIDDEN, assertThrows(ApiException.class,
                () -> service.checkin("tok", UUID.randomUUID(), Utilisateur.Role.PRESIDENT, "x")).getStatus());
    }

    @Test
    void secondCheckInIsRefused() {
        service.checkin("tok", presidentId, Utilisateur.Role.PRESIDENT, "P");
        assertEquals(HttpStatus.CONFLICT, assertThrows(ApiException.class,
                () -> service.checkin("tok", presidentId, Utilisateur.Role.PRESIDENT, "P")).getStatus());
    }

    @Test
    void notValidatedReservationCannotBoard() {
        reservation.setStatut(Reservation.STATUT_ANNULEE);
        assertEquals(HttpStatus.CONFLICT, assertThrows(ApiException.class,
                () -> service.checkin("tok", presidentId, Utilisateur.Role.PRESIDENT, "P")).getStatus());
        assertNull(recu.getCheckedAt());
    }

    @Test
    void unknownTokenIs404() {
        when(recuRepository.findByTokenForUpdate("zzz")).thenReturn(Optional.empty());
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ApiException.class,
                () -> service.checkin("zzz", presidentId, Utilisateur.Role.PRESIDENT, "P")).getStatus());
    }

    @Test
    void publicViewExposesNoSensitiveData() {
        RecuDTO full = service.toDto(recu);
        RecuDTO pub = RecuService.toPublicDto(full);
        assertNull(pub.getToken());
        assertNull(pub.getId());
        assertNull(pub.getMontant());
        assertNull(pub.getModePaiement());
        assertNull(pub.getUtilisateurIdInterne());
        assertNull(pub.getCooperativeTelephone());
        assertFalse(pub.getVoyageurNom().contains("Rakoto"));
        assertTrue(pub.getVoyageurTelephone().endsWith("67"));
        assertFalse(pub.getVoyageurTelephone().contains("034"));
        assertEquals("REC-2026-000001", pub.getNumeroRecu());
    }
}
