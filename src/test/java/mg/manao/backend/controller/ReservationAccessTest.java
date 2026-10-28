package mg.manao.backend.controller;

import mg.manao.backend.dto.ReservationDTO;
import mg.manao.backend.entity.Cooperative;
import mg.manao.backend.entity.Utilisateur;
import mg.manao.backend.exception.ApiException;
import mg.manao.backend.security.SecurityUserDetails;
import mg.manao.backend.service.CooperativeService;
import mg.manao.backend.service.ReservationService;
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

/** R3 : le paramètre utilisateur_id fourni par le client ne doit jamais élargir l'accès. */
class ReservationAccessTest {

    private ReservationService reservationService;
    private CooperativeService cooperativeService;
    private ReservationController controller;

    private final UUID voyageurA = UUID.randomUUID();
    private final UUID voyageurB = UUID.randomUUID();
    private final UUID presidentId = UUID.randomUUID();
    private final UUID coopId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        reservationService = mock(ReservationService.class);
        cooperativeService = mock(CooperativeService.class);
        controller = new ReservationController(reservationService, cooperativeService);
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

    private ReservationDTO dto(UUID utilisateurId) {
        return ReservationDTO.builder().id(UUID.randomUUID().toString()).utilisateurId(utilisateurId.toString()).build();
    }

    @Test
    void voyageurCannotReadAnotherUsersReservations() {
        loginAs(voyageurA, Utilisateur.Role.VOYAGEUR);

        ApiException e = assertThrows(ApiException.class,
                () -> controller.findAll(null, voyageurB.toString()));

        assertEquals(HttpStatus.FORBIDDEN, e.getStatus());
        verify(reservationService, never()).findForUtilisateur(any(), any());
    }

    @Test
    void voyageurSeesOnlyHisOwnReservations() {
        loginAs(voyageurA, Utilisateur.Role.VOYAGEUR);
        when(reservationService.findForUtilisateur(voyageurA, null)).thenReturn(List.of(dto(voyageurA)));

        controller.findAll(null, null);
        controller.findAll(null, voyageurA.toString());

        verify(reservationService, org.mockito.Mockito.times(2)).findForUtilisateur(voyageurA, null);
        verify(reservationService, never()).findAllForAdmin(any());
    }

    @Test
    void presidentCannotUseUtilisateurIdToReadOutsideHisCooperative() {
        loginAs(presidentId, Utilisateur.Role.PRESIDENT);
        Cooperative coop = Cooperative.builder().id(coopId).build();
        when(cooperativeService.getEntityForPresident(presidentId)).thenReturn(coop);
        // Sa coopérative ne contient que des réservations du voyageur A.
        when(reservationService.findAllForPresident(coopId, null)).thenReturn(List.of(dto(voyageurA)));

        List<ReservationDTO> autreUtilisateur = controller.findAll(null, voyageurB.toString());
        List<ReservationDTO> dansSaCooperative = controller.findAll(null, voyageurA.toString());

        assertEquals(0, autreUtilisateur.size());
        assertEquals(1, dansSaCooperative.size());
        // Jamais de lecture globale par utilisateur pour un Président.
        verify(reservationService, never()).findForUtilisateur(any(), any());
        verify(reservationService, never()).findAllForAdmin(any());
    }

    @Test
    void presidentWithoutFilterSeesOnlyHisCooperative() {
        loginAs(presidentId, Utilisateur.Role.PRESIDENT);
        Cooperative coop = Cooperative.builder().id(coopId).build();
        when(cooperativeService.getEntityForPresident(presidentId)).thenReturn(coop);
        when(reservationService.findAllForPresident(coopId, "Validée")).thenReturn(List.of(dto(voyageurA)));

        assertEquals(1, controller.findAll("Validée", null).size());
        verify(reservationService).findAllForPresident(coopId, "Validée");
    }

    @Test
    void adminKeepsFullAccess() {
        loginAs(UUID.randomUUID(), Utilisateur.Role.ADMIN);
        when(reservationService.findAllForAdmin(null)).thenReturn(List.of(dto(voyageurA), dto(voyageurB)));
        when(reservationService.findForUtilisateur(voyageurB, null)).thenReturn(List.of(dto(voyageurB)));

        assertEquals(2, controller.findAll(null, null).size());
        assertEquals(1, controller.findAll(null, voyageurB.toString()).size());
    }

    @Test
    void invalidUtilisateurIdIsABadRequest() {
        loginAs(voyageurA, Utilisateur.Role.VOYAGEUR);

        ApiException e = assertThrows(ApiException.class, () -> controller.findAll(null, "pas-un-uuid"));

        assertEquals(HttpStatus.BAD_REQUEST, e.getStatus());
    }

    @Test
    void unauthenticatedCallIsRejected() {
        assertThrows(ApiException.class, () -> controller.findAll(null, null));
    }

}
