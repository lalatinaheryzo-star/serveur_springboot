package mg.manao.backend.controller;

import mg.manao.backend.dto.ReserverPlaceRequest;
import mg.manao.backend.entity.Reservation;
import mg.manao.backend.entity.Utilisateur;
import mg.manao.backend.exception.ApiException;
import mg.manao.backend.security.SecurityUserDetails;
import mg.manao.backend.service.PlaceService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** R4 : l'identité vient de l'authentification, pas du corps de la requête. */
class PlaceAccessTest {

    private PlaceService placeService;
    private PlaceController controller;
    private final UUID moi = UUID.randomUUID();
    private final UUID autre = UUID.randomUUID();
    private final UUID placeId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        placeService = mock(PlaceService.class);
        controller = new PlaceController(placeService);
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private void loginAs(UUID id, Utilisateur.Role role) {
        Utilisateur u = Utilisateur.builder().id(id).email("x@example.com").motDePasse("h").role(role).build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(new SecurityUserDetails(u), null, List.of()));
    }

    private ReserverPlaceRequest pour(UUID id) {
        ReserverPlaceRequest r = new ReserverPlaceRequest();
        r.setUtilisateurId(id.toString());
        return r;
    }

    private Reservation reservationPour(UUID utilisateurId) {
        Utilisateur u = Utilisateur.builder().id(utilisateurId).nom("N").prenom("P").build();
        return Reservation.builder().id(UUID.randomUUID()).utilisateur(u).build();
    }

    @Test
    void voyageurCannotReserveInTheNameOfSomeoneElse() {
        loginAs(moi, Utilisateur.Role.VOYAGEUR);

        ApiException e = assertThrows(ApiException.class, () -> controller.reserver(placeId, pour(autre)));

        assertEquals(HttpStatus.FORBIDDEN, e.getStatus());
        verify(placeService, never()).reserverPlace(any(), any());
    }

    @Test
    void presidentCannotReserveInTheNameOfSomeoneElseEither() {
        loginAs(moi, Utilisateur.Role.PRESIDENT);

        assertThrows(ApiException.class, () -> controller.reserver(placeId, pour(autre)));
        verify(placeService, never()).reserverPlace(any(), any());
    }

    @Test
    void reservingWithMyOwnIdOrWithoutBodyUsesTheAuthenticatedUser() {
        loginAs(moi, Utilisateur.Role.VOYAGEUR);
        when(placeService.reserverPlace(placeId, moi)).thenReturn(reservationPour(moi));

        controller.reserver(placeId, pour(moi));
        controller.reserver(placeId, null);

        verify(placeService, org.mockito.Mockito.times(2)).reserverPlace(placeId, moi);
    }

    @Test
    void adminCanStillReserveForAVoyageur() {
        loginAs(moi, Utilisateur.Role.ADMIN);
        when(placeService.reserverPlace(placeId, autre)).thenReturn(reservationPour(autre));

        controller.reserver(placeId, pour(autre));

        verify(placeService).reserverPlace(placeId, autre);
    }

    @Test
    void releaseUsesTheAuthenticatedIdentityAndRole() {
        loginAs(moi, Utilisateur.Role.VOYAGEUR);

        controller.liberer(placeId);

        verify(placeService).libererPlace(placeId, moi, Utilisateur.Role.VOYAGEUR);
    }

    @Test
    void unauthenticatedReleaseAndReserveAreRejected() {
        assertThrows(ApiException.class, () -> controller.liberer(placeId));
        assertThrows(ApiException.class, () -> controller.reserver(placeId, null));
    }
}
