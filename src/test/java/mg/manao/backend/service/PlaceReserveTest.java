package mg.manao.backend.service;

import mg.manao.backend.entity.*;
import mg.manao.backend.exception.ApiException;
import mg.manao.backend.repository.PlaceRepository;
import mg.manao.backend.repository.ReservationRepository;
import mg.manao.backend.repository.UtilisateurRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** Groupe 2 : réservation d'une place, voyage parti/annulé, réutilisation après annulation. */
class PlaceReserveTest {

    private PlaceRepository placeRepository;
    private ReservationRepository reservationRepository;
    private UtilisateurRepository utilisateurRepository;
    private PlaceService service;

    private final UUID userId = UUID.randomUUID();
    private Voyage voyage;
    private Place place;

    @BeforeEach
    void setUp() {
        placeRepository = mock(PlaceRepository.class);
        reservationRepository = mock(ReservationRepository.class);
        utilisateurRepository = mock(UtilisateurRepository.class);
        service = new PlaceService(placeRepository, reservationRepository, utilisateurRepository, mock(VoyageService.class));

        voyage = Voyage.builder().id(UUID.randomUUID()).statut("actif")
                .dateDepart(LocalDate.now(ZoneId.of("Indian/Antananarivo")).plusDays(2))
                .heureDepart(LocalTime.NOON).build();
        place = Place.builder().id(UUID.randomUUID()).voyage(voyage).numeroPlace(1).statut("disponible").build();
        when(placeRepository.findByIdForUpdate(place.getId())).thenReturn(Optional.of(place));
        when(utilisateurRepository.findById(userId)).thenReturn(Optional.of(Utilisateur.builder().id(userId).build()));
        when(reservationRepository.save(any(Reservation.class))).thenAnswer(i -> i.getArgument(0));
        when(placeRepository.countByVoyageIdAndStatut(any(), eq("disponible"))).thenReturn(5L);
    }

    @Test
    void availablePlaceIsReservedAndReservationIsPending() {
        Reservation r = service.reserverPlace(place.getId(), userId);
        assertEquals("reservee", place.getStatut());
        assertEquals(Reservation.STATUT_EN_ATTENTE, r.getStatut());
        assertSame(place, r.getPlace());
    }

    @Test
    void secondRequestOnTheSamePlaceGets409() {
        service.reserverPlace(place.getId(), userId);
        ApiException e = assertThrows(ApiException.class, () -> service.reserverPlace(place.getId(), userId));
        assertEquals(HttpStatus.CONFLICT, e.getStatus());
        verify(reservationRepository, times(1)).save(any());
    }

    @Test
    void placeFreedByCancellationCanBeReservedAgain() {
        Reservation r1 = service.reserverPlace(place.getId(), userId);
        r1.setStatut(Reservation.STATUT_ANNULEE);
        service.rendrePlaceDisponible(r1);
        assertEquals("disponible", place.getStatut());
        assertNull(r1.getPlace());

        Reservation r2 = service.reserverPlace(place.getId(), userId);
        assertSame(place, r2.getPlace());
        assertNull(r1.getPlace()); // une seule réservation pointe vers la place
    }

    @Test
    void departedVoyageCannotBeBooked() {
        voyage.setDateDepart(LocalDate.now(ZoneId.of("Indian/Antananarivo")).minusDays(1));
        assertEquals(HttpStatus.CONFLICT, assertThrows(ApiException.class,
                () -> service.reserverPlace(place.getId(), userId)).getStatus());
        assertEquals("disponible", place.getStatut());
    }

    @Test
    void cancelledVoyageCannotBeBooked() {
        voyage.setStatut("annulé");
        assertEquals(HttpStatus.CONFLICT, assertThrows(ApiException.class,
                () -> service.reserverPlace(place.getId(), userId)).getStatus());
    }

    @Test
    void lastPlaceMarksVoyageFull() {
        when(placeRepository.countByVoyageIdAndStatut(any(), eq("disponible"))).thenReturn(0L);
        service.reserverPlace(place.getId(), userId);
        assertEquals("complet", voyage.getStatut());
    }

    @Test
    void releasingAPlaceReopensAFullVoyage() {
        voyage.setStatut("complet");
        place.setStatut("reservee");
        Reservation r = Reservation.builder().voyage(voyage).place(place).statut(Reservation.STATUT_ANNULEE).build();
        service.rendrePlaceDisponible(r);
        assertEquals("actif", voyage.getStatut());
        assertEquals("disponible", place.getStatut());
    }
}
