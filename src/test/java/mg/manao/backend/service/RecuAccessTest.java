package mg.manao.backend.service;

import mg.manao.backend.dto.RecuIO;
import mg.manao.backend.entity.Cooperative;
import mg.manao.backend.entity.Paiement;
import mg.manao.backend.entity.Reservation;
import mg.manao.backend.entity.Utilisateur;
import mg.manao.backend.entity.Voyage;
import mg.manao.backend.exception.ApiException;
import mg.manao.backend.repository.PaiementRepository;
import mg.manao.backend.repository.RecuRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** R4 : un reçu n'est ni créé ni généré pour la réservation d'un autre utilisateur. */
class RecuAccessTest {

    private RecuRepository recuRepository;
    private PaiementRepository paiementRepository;
    private RecuService service;

    private final UUID proprietaire = UUID.randomUUID();
    private final UUID intrus = UUID.randomUUID();
    private final UUID presidentDeLaCoop = UUID.randomUUID();
    private final UUID autrePresident = UUID.randomUUID();
    private final UUID reservationId = UUID.randomUUID();
    private final UUID paiementId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        recuRepository = mock(RecuRepository.class);
        paiementRepository = mock(PaiementRepository.class);
        service = new RecuService(recuRepository, paiementRepository, mock(PdfService.class));

        Utilisateur president = Utilisateur.builder().id(presidentDeLaCoop).build();
        Cooperative coop = Cooperative.builder().id(UUID.randomUUID()).president(president).build();
        Voyage voyage = Voyage.builder().id(UUID.randomUUID()).cooperative(coop).build();
        Utilisateur voyageur = Utilisateur.builder().id(proprietaire).build();
        Reservation reservation = Reservation.builder().id(reservationId).voyage(voyage).utilisateur(voyageur).build();
        Paiement paiement = Paiement.builder().id(paiementId).reservation(reservation)
                .statut(Paiement.STATUT_REUSSI).build();

        when(paiementRepository.findById(paiementId)).thenReturn(Optional.of(paiement));
        when(paiementRepository.findByReservationIdWithDetails(reservationId)).thenReturn(Optional.of(paiement));
        when(recuRepository.findByReservationIdWithDetails(reservationId)).thenReturn(Optional.empty());
        when(recuRepository.findByPaiementId(paiementId)).thenReturn(Optional.empty());
    }

    private RecuIO.CreateRecuRequest requete() {
        RecuIO.CreateRecuRequest r = new RecuIO.CreateRecuRequest();
        r.setPaiementId(paiementId.toString());
        return r;
    }

    @Test
    void anotherVoyageurCannotCreateAReceiptForSomeoneElsesPayment() {
        ApiException e = assertThrows(ApiException.class,
                () -> service.create(requete(), intrus, Utilisateur.Role.VOYAGEUR));

        assertEquals(HttpStatus.FORBIDDEN, e.getStatus());
        verify(recuRepository, never()).save(any());
    }

    @Test
    void aPresidentOfAnotherCooperativeCannotCreateTheReceipt() {
        assertThrows(ApiException.class,
                () -> service.create(requete(), autrePresident, Utilisateur.Role.PRESIDENT));
        verify(recuRepository, never()).save(any());
    }

    @Test
    void lookingUpAnotherUsersReceiptDoesNotGenerateOne() {
        ApiException e = assertThrows(ApiException.class,
                () -> service.findByReservation(reservationId, intrus, Utilisateur.Role.VOYAGEUR));

        assertEquals(HttpStatus.FORBIDDEN, e.getStatus());
        // Effet de bord évité : aucun reçu n'est généré pour une réservation qui n'est pas la sienne.
        verify(recuRepository, never()).save(any());
    }

    @Test
    void aPresidentOfAnotherCooperativeCannotTriggerReceiptGeneration() {
        assertThrows(ApiException.class,
                () -> service.findByReservation(reservationId, autrePresident, Utilisateur.Role.PRESIDENT));
        verify(recuRepository, never()).save(any());
    }
}
