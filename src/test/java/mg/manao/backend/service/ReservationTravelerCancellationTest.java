package mg.manao.backend.service;

import mg.manao.backend.entity.Paiement;
import mg.manao.backend.entity.Place;
import mg.manao.backend.entity.Reservation;
import mg.manao.backend.entity.Utilisateur;
import mg.manao.backend.entity.Voyage;
import mg.manao.backend.exception.ApiException;
import mg.manao.backend.repository.PaiementRepository;
import mg.manao.backend.repository.PlaceRepository;
import mg.manao.backend.repository.ReservationRepository;
import mg.manao.backend.repository.UtilisateurRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReservationTravelerCancellationTest {

    private final UUID ownerId = UUID.randomUUID();
    private final UUID reservationId = UUID.randomUUID();
    private ReservationRepository reservationRepository;
    private PaiementRepository paiementRepository;
    private PlaceRepository placeRepository;
    private PlaceService placeService;
    private Reservation reservation;
    private Place place;

    @BeforeEach
    void setUp() {
        reservationRepository = mock(ReservationRepository.class);
        paiementRepository = mock(PaiementRepository.class);
        placeRepository = mock(PlaceRepository.class);
        placeService = new PlaceService(
                placeRepository,
                reservationRepository,
                paiementRepository,
                mock(UtilisateurRepository.class),
                mock(VoyageService.class));

        Voyage voyage = Voyage.builder().id(UUID.randomUUID()).statut("complet").build();
        place = Place.builder()
                .id(UUID.randomUUID())
                .voyage(voyage)
                .numeroPlace(3)
                .statut("reservee")
                .build();
        reservation = Reservation.builder()
                .id(reservationId)
                .voyage(voyage)
                .utilisateur(Utilisateur.builder().id(ownerId).build())
                .place(place)
                .numeroPlace(3)
                .statut(Reservation.STATUT_EN_ATTENTE)
                .build();
        when(reservationRepository.findByIdForUpdate(reservationId))
                .thenReturn(Optional.of(reservation));
        when(paiementRepository.findByReservationId(reservationId))
                .thenReturn(Optional.empty());
        when(placeRepository.findByIdForUpdate(place.getId()))
                .thenReturn(Optional.of(place));
        when(reservationRepository.save(any(Reservation.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void ownerCanCancelPendingUnpaidReservationAndReleaseItsSeat() {
        placeService.annulerReservationVoyageur(reservationId, ownerId);

        assertEquals(Reservation.STATUT_ANNULEE, reservation.getStatut());
        assertEquals("disponible", place.getStatut());
        assertEquals("actif", place.getVoyage().getStatut());
        assertNull(reservation.getPlace());
        verify(reservationRepository).save(reservation);
    }

    @Test
    void anotherTravelerCannotCancelTheReservation() {
        ApiException error = assertThrows(
                ApiException.class,
                () -> placeService.annulerReservationVoyageur(reservationId, UUID.randomUUID()));

        assertEquals(HttpStatus.FORBIDDEN, error.getStatus());
        assertPendingAndSeatUnchanged();
    }

    @Test
    void validatedReservationCannotBeCancelledByTraveler() {
        reservation.setStatut(Reservation.STATUT_VALIDEE);

        ApiException error = assertThrows(
                ApiException.class,
                () -> placeService.annulerReservationVoyageur(reservationId, ownerId));

        assertEquals(HttpStatus.CONFLICT, error.getStatus());
        assertPendingAndSeatUnchanged(Reservation.STATUT_VALIDEE);
    }

    @Test
    void cancelledReservationCannotBeCancelledAgain() {
        reservation.setStatut(Reservation.STATUT_ANNULEE);

        ApiException error = assertThrows(
                ApiException.class,
                () -> placeService.annulerReservationVoyageur(reservationId, ownerId));

        assertEquals(HttpStatus.CONFLICT, error.getStatus());
        assertPendingAndSeatUnchanged(Reservation.STATUT_ANNULEE);
    }

    @Test
    void reservationWithAnyPaymentCannotBeCancelled() {
        for (String paymentStatus : new String[] {
                Paiement.STATUT_EN_ATTENTE,
                Paiement.STATUT_REUSSI,
                Paiement.STATUT_ECHOUE
        }) {
            when(paiementRepository.findByReservationId(reservationId))
                    .thenReturn(Optional.of(Paiement.builder()
                            .id(UUID.randomUUID())
                            .reservation(reservation)
                            .statut(paymentStatus)
                            .build()));

            ApiException error = assertThrows(
                    ApiException.class,
                    () -> placeService.annulerReservationVoyageur(reservationId, ownerId));

            assertEquals(HttpStatus.CONFLICT, error.getStatus());
            assertPendingAndSeatUnchanged();
        }
    }

    @Test
    void missingSeatPreventsCancellationWithoutChangingReservation() {
        reservation.setPlace(null);

        ApiException error = assertThrows(
                ApiException.class,
                () -> placeService.annulerReservationVoyageur(reservationId, ownerId));

        assertEquals(HttpStatus.CONFLICT, error.getStatus());
        assertPendingAndSeatUnchanged();
    }

    private void assertPendingAndSeatUnchanged() {
        assertPendingAndSeatUnchanged(Reservation.STATUT_EN_ATTENTE);
    }

    private void assertPendingAndSeatUnchanged(String expectedStatus) {
        assertEquals(expectedStatus, reservation.getStatut());
        assertEquals("reservee", place.getStatut());
        verify(placeRepository, never()).save(any());
        verify(reservationRepository, never()).save(any());
    }
}
