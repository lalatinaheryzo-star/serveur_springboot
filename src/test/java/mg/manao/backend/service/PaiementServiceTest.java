package mg.manao.backend.service;

import mg.manao.backend.dto.PaiementDTO;
import mg.manao.backend.dto.PaiementRequest;
import mg.manao.backend.dto.UpdatePaiementStatutRequest;
import mg.manao.backend.entity.Cooperative;
import mg.manao.backend.entity.Paiement;
import mg.manao.backend.entity.Reservation;
import mg.manao.backend.entity.Voyage;
import mg.manao.backend.exception.ApiException;
import mg.manao.backend.repository.PaiementRepository;
import mg.manao.backend.repository.ReservationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Groupe 1 : montant serveur, statut initial « En attente », transitions, reçu conditionnel. */
class PaiementServiceTest {

    private PaiementRepository paiementRepository;
    private ReservationRepository reservationRepository;
    private RecuService recuService;
    private PaiementService service;

    private Cooperative coop;
    private Reservation reservation;

    @BeforeEach
    void setUp() {
        paiementRepository = mock(PaiementRepository.class);
        reservationRepository = mock(ReservationRepository.class);
        recuService = mock(RecuService.class);
        service = new PaiementService(paiementRepository, reservationRepository, recuService);

        coop = Cooperative.builder().id(UUID.randomUUID()).build();
        Voyage voyage = Voyage.builder().id(UUID.randomUUID()).cooperative(coop)
                .prix(new BigDecimal("25000")).build();
        reservation = Reservation.builder().id(UUID.randomUUID()).voyage(voyage)
                .statut(Reservation.STATUT_VALIDEE).build();
        when(reservationRepository.findById(reservation.getId())).thenReturn(Optional.of(reservation));
        when(paiementRepository.findByReservationId(reservation.getId())).thenReturn(Optional.empty());
        when(paiementRepository.save(any(Paiement.class))).thenAnswer(i -> {
            Paiement p = i.getArgument(0);
            if (p.getId() == null) p.setId(UUID.randomUUID());
            return p;
        });
    }

    private PaiementRequest request(String montant, String statut) {
        PaiementRequest r = new PaiementRequest();
        r.setReservationId(reservation.getId().toString());
        r.setMontant(new BigDecimal(montant));
        r.setModePaiement("Mobile Money");
        r.setStatut(statut);
        return r;
    }

    private Paiement paiement(String statut) {
        Paiement p = Paiement.builder().id(UUID.randomUUID()).reservation(reservation)
                .montant(new BigDecimal("25000")).modePaiement("Espèces").statut(statut).build();
        when(paiementRepository.findById(p.getId())).thenReturn(Optional.of(p));
        return p;
    }

    private UpdatePaiementStatutRequest statut(String s) {
        UpdatePaiementStatutRequest r = new UpdatePaiementStatutRequest();
        r.setStatut(s);
        return r;
    }

    @Test
    void clientAmountIsIgnoredAndServerPriceIsUsed() {
        PaiementDTO dto = service.create(request("1", null));
        assertEquals(0, new BigDecimal("25000").compareTo(dto.getMontant()));
    }

    @Test
    void clientCannotForceSuccessStatus() {
        PaiementDTO dto = service.create(request("25000", "Réussi"));
        assertEquals(Paiement.STATUT_EN_ATTENTE, dto.getStatut());
    }

    @Test
    void paymentModeIsRequired() {
        PaiementRequest r = request("25000", null);
        r.setModePaiement(" ");
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ApiException.class, () -> service.create(r)).getStatus());
    }

    @Test
    void secondPaymentForSameReservationIsRejected() {
        Paiement existingPayment = Paiement.builder()
                .id(UUID.randomUUID())
                .reservation(reservation)
                .montant(new BigDecimal("25000"))
                .modePaiement("Mobile Money")
                .statut(Paiement.STATUT_EN_ATTENTE)
                .build();

        when(paiementRepository.findByReservationId(reservation.getId()))
                .thenReturn(Optional.of(existingPayment));

        assertEquals(HttpStatus.CONFLICT, assertThrows(ApiException.class,
                () -> service.create(request("25000", null))).getStatus());
    }

    @Test
    void refusedReservationCannotBePaid() {
        reservation.setStatut(Reservation.STATUT_REFUSEE);
        assertEquals(HttpStatus.CONFLICT, assertThrows(ApiException.class,
                () -> service.create(request("25000", null))).getStatus());
        verify(paiementRepository, never()).save(any());
    }

    @Test
    void presidentConfirmsPaymentAndReceiptIsRequestedIdempotently() {
        Paiement p = paiement(Paiement.STATUT_EN_ATTENTE);
        service.updateStatut(p.getId(), statut("Réussi"), coop);
        assertEquals(Paiement.STATUT_REUSSI, p.getStatut());
        verify(recuService).ensureRecuForPaiement(p);
    }

    @Test
    void unknownStatusIsRejected() {
        Paiement p = paiement(Paiement.STATUT_EN_ATTENTE);
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ApiException.class,
                () -> service.updateStatut(p.getId(), statut("Bidon"), coop)).getStatus());
    }

    @Test
    void successfulPaymentCannotGoBack() {
        Paiement p = paiement(Paiement.STATUT_REUSSI);
        assertEquals(HttpStatus.CONFLICT, assertThrows(ApiException.class,
                () -> service.updateStatut(p.getId(), statut("Échoué"), coop)).getStatus());
        assertEquals(Paiement.STATUT_REUSSI, p.getStatut());
    }

    @Test
    void otherCooperativeCannotConfirm() {
        Paiement p = paiement(Paiement.STATUT_EN_ATTENTE);
        Cooperative autre = Cooperative.builder().id(UUID.randomUUID()).build();
        assertEquals(HttpStatus.FORBIDDEN, assertThrows(ApiException.class,
                () -> service.updateStatut(p.getId(), statut("Réussi"), autre)).getStatus());
        assertEquals(Paiement.STATUT_EN_ATTENTE, p.getStatut());
    }

    @Test
    void cannotConfirmPaymentOfCancelledReservation() {
        reservation.setStatut(Reservation.STATUT_ANNULEE);
        Paiement p = paiement(Paiement.STATUT_EN_ATTENTE);
        assertEquals(HttpStatus.CONFLICT, assertThrows(ApiException.class,
                () -> service.updateStatut(p.getId(), statut("Réussi"), coop)).getStatus());
        assertEquals(Paiement.STATUT_EN_ATTENTE, p.getStatut());
    }
}
