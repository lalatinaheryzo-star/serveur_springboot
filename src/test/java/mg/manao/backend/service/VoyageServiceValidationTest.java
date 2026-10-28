package mg.manao.backend.service;

import mg.manao.backend.dto.VoyageRequest;
import mg.manao.backend.entity.Cooperative;
import mg.manao.backend.entity.Voyage;
import mg.manao.backend.exception.ApiException;
import mg.manao.backend.repository.PlaceRepository;
import mg.manao.backend.repository.VoyageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Groupe 3 : validation des prix, capacités, dates et statuts d'un voyage. */
class VoyageServiceValidationTest {

    private VoyageRepository voyageRepository;
    private PlaceRepository placeRepository;
    private VoyageService service;
    private final Cooperative coop = Cooperative.builder().id(UUID.randomUUID()).build();

    @BeforeEach
    void setUp() {
        voyageRepository = mock(VoyageRepository.class);
        placeRepository = mock(PlaceRepository.class);
        service = new VoyageService(voyageRepository, placeRepository);
        when(voyageRepository.save(any(Voyage.class))).thenAnswer(i -> {
            Voyage v = i.getArgument(0);
            if (v.getId() == null) v.setId(UUID.randomUUID());
            return v;
        });
        when(placeRepository.findByVoyageIdOrderByNumeroPlaceAsc(any())).thenReturn(List.of());
    }

    private VoyageRequest ok() {
        VoyageRequest r = new VoyageRequest();
        r.setVilleDepart("Antananarivo");
        r.setVilleArrivee("Toamasina");
        r.setDateDepart(LocalDate.now(ZoneId.of("Indian/Antananarivo")).plusDays(3).toString());
        r.setHeureDepart("08:00");
        r.setPrix(new BigDecimal("30000"));
        r.setCapacite(18);
        r.setStatut("actif");
        return r;
    }

    private void assert400(VoyageRequest r) {
        ApiException e = assertThrows(ApiException.class, () -> service.create(r, coop));
        assertEquals(HttpStatus.BAD_REQUEST, e.getStatus());
        verify(voyageRepository, never()).save(any());
    }

    @Test void validRequestIsAccepted() { assertNotNull(service.create(ok(), coop)); }
    @Test void negativePriceRejected() { VoyageRequest r = ok(); r.setPrix(new BigDecimal("-1")); assert400(r); }
    @Test void absurdPriceRejected() { VoyageRequest r = ok(); r.setPrix(new BigDecimal("999999999")); assert400(r); }
    @Test void zeroCapacityRejected() { VoyageRequest r = ok(); r.setCapacite(0); assert400(r); }
    @Test void negativeCapacityRejected() { VoyageRequest r = ok(); r.setCapacite(-5); assert400(r); }
    @Test void hugeCapacityRejected() { VoyageRequest r = ok(); r.setCapacite(100000); assert400(r); }
    @Test void unknownStatusRejected() { VoyageRequest r = ok(); r.setStatut("gratuit"); assert400(r); }
    @Test void pastDateRejectedOnCreate() { VoyageRequest r = ok(); r.setDateDepart("2000-01-01"); assert400(r); }
    @Test void sameCitiesRejected() { VoyageRequest r = ok(); r.setVilleArrivee(" antananarivo "); assert400(r); }
    @Test void invalidDateFormatRejected() { VoyageRequest r = ok(); r.setDateDepart("demain"); assert400(r); }

    @Test
    void capacityCannotDropBelowExistingPlaces() {
        Voyage existant = Voyage.builder().id(UUID.randomUUID()).cooperative(coop).capacite(18)
                .dateDepart(LocalDate.parse(ok().getDateDepart())).build();
        when(voyageRepository.findById(existant.getId())).thenReturn(java.util.Optional.of(existant));
        when(placeRepository.findByVoyageIdOrderByNumeroPlaceAsc(existant.getId()))
                .thenReturn(java.util.Collections.nCopies(18, null));
        VoyageRequest r = ok(); r.setCapacite(10);
        assertEquals(HttpStatus.CONFLICT, assertThrows(ApiException.class,
                () -> service.update(existant.getId(), r, coop)).getStatus());
    }
}
