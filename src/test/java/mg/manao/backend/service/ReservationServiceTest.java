package mg.manao.backend.service;

import mg.manao.backend.dto.ReservationRequest;
import mg.manao.backend.dto.UpdateReservationStatutRequest;
import mg.manao.backend.entity.*;
import mg.manao.backend.exception.ApiException;
import mg.manao.backend.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Groupe 2 : machine d'états des réservations, libération de place, création manuelle sans survente. */
class ReservationServiceTest {

    private ReservationRepository reservationRepository;
    private VoyageRepository voyageRepository;
    private PlaceRepository placeRepository;
    private PaiementRepository paiementRepository;
    private PlaceService placeService;
    private RecuService recuService;
    private EmailService emailService;
    private ReservationService service;

    private final UUID presidentId = UUID.randomUUID();
    private Place place;
    private Reservation reservation;

    @BeforeEach
    void setUp() {
        reservationRepository = mock(ReservationRepository.class);
        voyageRepository = mock(VoyageRepository.class);
        placeRepository = mock(PlaceRepository.class);
        paiementRepository = mock(PaiementRepository.class);
        placeService = mock(PlaceService.class);
        recuService = mock(RecuService.class);
        emailService = mock(EmailService.class);
        service = new ReservationService(reservationRepository, voyageRepository,
                mock(UtilisateurRepository.class), placeRepository, emailService,
                placeService, paiementRepository, recuService);

        Voyage voyage = Voyage.builder().id(UUID.randomUUID()).statut("actif").build();
        place = Place.builder().id(UUID.randomUUID()).voyage(voyage).numeroPlace(4).statut("reservee").build();
        reservation = Reservation.builder().id(UUID.randomUUID()).voyage(voyage).place(place)
                .numeroPlace(4).statut(Reservation.STATUT_EN_ATTENTE).build();
        when(reservationRepository.findByIdForPresidentWithDetails(reservation.getId(), presidentId))
                .thenReturn(Optional.of(reservation));
        when(reservationRepository.save(any(Reservation.class))).thenAnswer(i -> i.getArgument(0));
    }

    private UpdateReservationStatutRequest statut(String s) {
        UpdateReservationStatutRequest r = new UpdateReservationStatutRequest();
        r.setStatut(s);
        return r;
    }

    @Test
    void refusalFreesThePlaceThroughPlaceService() {
        service.updateStatut(reservation.getId(), statut("Refusée"), presidentId);
        assertEquals(Reservation.STATUT_REFUSEE, reservation.getStatut());
        verify(placeService).rendrePlaceDisponible(reservation);
    }

    @Test
    void cancelledReservationCannotBeRevived() {
        reservation.setStatut(Reservation.STATUT_ANNULEE);
        ApiException e = assertThrows(ApiException.class,
                () -> service.updateStatut(reservation.getId(), statut("Validée"), presidentId));
        assertEquals(HttpStatus.CONFLICT, e.getStatus());
        assertEquals(Reservation.STATUT_ANNULEE, reservation.getStatut());
        verify(placeService, never()).rendrePlaceDisponible(any());
    }

    @Test
    void refusedReservationCannotBeRevived() {
        reservation.setStatut(Reservation.STATUT_REFUSEE);
        assertEquals(HttpStatus.CONFLICT, assertThrows(ApiException.class,
                () -> service.updateStatut(reservation.getId(), statut("En attente"), presidentId)).getStatus());
    }

    @Test
    void unknownStatusIsRejected() {
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ApiException.class,
                () -> service.updateStatut(reservation.getId(), statut("Bidon"), presidentId)).getStatus());
    }

    @Test
    void validationGeneratesReceiptWhenPaymentAlreadySucceeded() {
        Paiement p = Paiement.builder().id(UUID.randomUUID()).reservation(reservation)
                .statut(Paiement.STATUT_REUSSI).build();
        when(paiementRepository.findByReservationId(reservation.getId())).thenReturn(Optional.of(p));

        service.updateStatut(reservation.getId(), statut("Validée"), presidentId);

        assertEquals(Reservation.STATUT_VALIDEE, reservation.getStatut());
        verify(recuService).ensureRecuForPaiement(p);
    }

    @Test
    void validationWithoutPaymentCreatesNoReceipt() {
        when(paiementRepository.findByReservationId(reservation.getId())).thenReturn(Optional.empty());
        service.updateStatut(reservation.getId(), statut("Validée"), presidentId);
        verify(recuService, never()).ensureRecuForPaiement(any());
    }

    @Test
    void manualCreationRequiresASeatNumber() {
        ReservationRequest req = new ReservationRequest();
        req.setVoyageId(UUID.randomUUID().toString());
        req.setUtilisateurId(UUID.randomUUID().toString());
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ApiException.class,
                () -> service.createManual(req)).getStatus());
    }

    @Test
    void manualCreationGoesThroughTheLockedReservationPath() {
        UUID voyageId = place.getVoyage().getId();
        UUID userId = UUID.randomUUID();
        ReservationRequest req = new ReservationRequest();
        req.setVoyageId(voyageId.toString());
        req.setUtilisateurId(userId.toString());
        req.setNumeroPlace(4);
        when(voyageRepository.findById(voyageId)).thenReturn(Optional.of(place.getVoyage()));
        when(placeRepository.findByVoyageIdAndNumeroPlace(voyageId, 4)).thenReturn(Optional.of(place));
        when(placeService.reserverPlace(place.getId(), userId)).thenReturn(reservation);

        service.createManual(req);

        verify(placeService).reserverPlace(place.getId(), userId);
    }

    @Test
    void deletingReleasesTheSeat() {
        when(reservationRepository.findById(reservation.getId())).thenReturn(Optional.of(reservation));
        service.delete(reservation.getId());
        verify(placeService).rendrePlaceDisponible(reservation);
        verify(reservationRepository).deleteById(reservation.getId());
    }
}
