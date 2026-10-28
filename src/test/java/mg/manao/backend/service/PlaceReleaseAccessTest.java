package mg.manao.backend.service;

import mg.manao.backend.entity.Cooperative;
import mg.manao.backend.entity.Place;
import mg.manao.backend.entity.Reservation;
import mg.manao.backend.entity.Utilisateur;
import mg.manao.backend.entity.Voyage;
import mg.manao.backend.exception.ApiException;
import mg.manao.backend.repository.PlaceRepository;
import mg.manao.backend.repository.ReservationRepository;
import mg.manao.backend.repository.UtilisateurRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** R4 : seul le propriétaire, le Président de la coopérative ou l'ADMIN peut libérer une place. */
class PlaceReleaseAccessTest {

    private PlaceRepository placeRepository;
    private ReservationRepository reservationRepository;
    private PlaceService service;

    private final UUID proprietaire = UUID.randomUUID();
    private final UUID intrus = UUID.randomUUID();
    private final UUID presidentDeLaCoop = UUID.randomUUID();
    private final UUID autrePresident = UUID.randomUUID();
    private final UUID placeId = UUID.randomUUID();

    private Place place;
    private Reservation reservation;

    @BeforeEach
    void setUp() {
        placeRepository = mock(PlaceRepository.class);
        reservationRepository = mock(ReservationRepository.class);
        service = new PlaceService(placeRepository, reservationRepository,
                mock(UtilisateurRepository.class), mock(VoyageService.class));

        Utilisateur president = Utilisateur.builder().id(presidentDeLaCoop).build();
        Cooperative coop = Cooperative.builder().id(UUID.randomUUID()).president(president).build();
        Voyage voyage = Voyage.builder().id(UUID.randomUUID()).statut("actif").cooperative(coop).build();
        place = Place.builder().id(placeId).voyage(voyage).numeroPlace(3).statut("reservee").build();
        Utilisateur voyageur = Utilisateur.builder().id(proprietaire).build();
        reservation = Reservation.builder().id(UUID.randomUUID()).voyage(voyage).utilisateur(voyageur)
                .place(place).statut(Reservation.STATUT_EN_ATTENTE).build();

        when(placeRepository.findByIdForUpdate(placeId)).thenReturn(Optional.of(place));
        when(reservationRepository.findByPlaceIdAndStatutIn(eq(placeId), any())).thenReturn(List.of(reservation));
    }

    private void assertRefuseEtInchange(UUID userId, Utilisateur.Role role) {
        ApiException e = assertThrows(ApiException.class, () -> service.libererPlace(placeId, userId, role));
        assertEquals(HttpStatus.FORBIDDEN, e.getStatus());
        assertEquals("reservee", place.getStatut());
        assertEquals(Reservation.STATUT_EN_ATTENTE, reservation.getStatut());
        verify(placeRepository, never()).save(any());
        verify(reservationRepository, never()).save(any());
    }

    @Test
    void anotherVoyageurCannotReleaseSomeoneElsesPlace() {
        assertRefuseEtInchange(intrus, Utilisateur.Role.VOYAGEUR);
    }

    @Test
    void aPresidentOfAnotherCooperativeCannotReleaseThePlace() {
        assertRefuseEtInchange(autrePresident, Utilisateur.Role.PRESIDENT);
    }

    @Test
    void theOwnerCanReleaseHisPlaceAndHisReservationIsCancelled() {
        service.libererPlace(placeId, proprietaire, Utilisateur.Role.VOYAGEUR);

        assertEquals("disponible", place.getStatut());
        assertEquals(Reservation.STATUT_ANNULEE, reservation.getStatut());
        // Le lien est détaché : la place pourra être re-réservée sans doublon.
        assertEquals(null, reservation.getPlace());
    }

    @Test
    void thePresidentOfTheCooperativeCanReleaseThePlace() {
        service.libererPlace(placeId, presidentDeLaCoop, Utilisateur.Role.PRESIDENT);

        assertEquals("disponible", place.getStatut());
    }

    @Test
    void adminCanStillReleaseAnyPlace() {
        service.libererPlace(placeId, UUID.randomUUID(), Utilisateur.Role.ADMIN);

        assertEquals("disponible", place.getStatut());
        assertEquals(Reservation.STATUT_ANNULEE, reservation.getStatut());
    }

    @Test
    void aVoyageurCannotReleaseAPlaceThatHasNoReservationOfHis() {
        when(reservationRepository.findByPlaceIdAndStatutIn(eq(placeId), any())).thenReturn(List.of());

        assertRefuseEtInchange(intrus, Utilisateur.Role.VOYAGEUR);
    }

    @Test
    void theOwnerCannotReleaseAPlaceWhoseReservationIsAlreadyValidated() {
        reservation.setStatut(Reservation.STATUT_VALIDEE);

        ApiException e = assertThrows(ApiException.class,
                () -> service.libererPlace(placeId, proprietaire, Utilisateur.Role.VOYAGEUR));

        assertEquals(HttpStatus.FORBIDDEN, e.getStatus());
        assertEquals("reservee", place.getStatut());
        assertEquals(Reservation.STATUT_VALIDEE, reservation.getStatut());
        verify(placeRepository, never()).save(any());
    }

    @Test
    void thePresidentCanStillReleaseAValidatedReservationOfHisCooperative() {
        reservation.setStatut(Reservation.STATUT_VALIDEE);

        service.libererPlace(placeId, presidentDeLaCoop, Utilisateur.Role.PRESIDENT);

        assertEquals("disponible", place.getStatut());
    }
}
