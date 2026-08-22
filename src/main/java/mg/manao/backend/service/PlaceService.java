package mg.manao.backend.service;

import lombok.RequiredArgsConstructor;
import mg.manao.backend.dto.PlaceDTO;
import mg.manao.backend.entity.Place;
import mg.manao.backend.entity.Reservation;
import mg.manao.backend.entity.Utilisateur;
import mg.manao.backend.entity.Voyage;
import mg.manao.backend.exception.ApiException;
import mg.manao.backend.repository.PlaceRepository;
import mg.manao.backend.repository.ReservationRepository;
import mg.manao.backend.repository.UtilisateurRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PlaceService {

    private final PlaceRepository placeRepository;
    private final ReservationRepository reservationRepository;
    private final UtilisateurRepository utilisateurRepository;
    private final VoyageService voyageService;

    public static PlaceDTO toDto(Place p) {
        return PlaceDTO.builder()
                .id(p.getId().toString())
                .numeroPlace(p.getNumeroPlace())
                .statut(p.getStatut())
                .build();
    }

    public List<PlaceDTO> findByVoyage(UUID voyageId) {
        voyageService.getEntity(voyageId); // 404 si le voyage n'existe pas
        return placeRepository.findByVoyageIdOrderByNumeroPlaceAsc(voyageId).stream()
                .map(PlaceService::toDto)
                .toList();
    }

    /**
     * Réserve atomiquement une place et crée la réservation associée.
     * Verrou pessimiste sur la ligne `place` : deux requêtes concurrentes sur
     * la même place ne peuvent jamais aboutir toutes les deux (§ paiement
     * concurrent — évite la survente d'un siège).
     */
    @Transactional
    public Reservation reserverPlace(UUID placeId, UUID utilisateurId) {
        Place place = placeRepository.findByIdForUpdate(placeId)
                .orElseThrow(() -> ApiException.notFound("Place introuvable."));

        if (!"disponible".equals(place.getStatut())) {
            throw ApiException.conflict("Cette place n'est plus disponible.");
        }

        Voyage voyage = place.getVoyage();
        if ("annulé".equalsIgnoreCase(voyage.getStatut())) {
            throw ApiException.conflict("Ce voyage a été annulé.");
        }

        Utilisateur utilisateur = utilisateurRepository.findById(utilisateurId)
                .orElseThrow(() -> ApiException.badRequest("Utilisateur introuvable."));

        place.setStatut("reservee");
        placeRepository.save(place);

        Reservation reservation = Reservation.builder()
                .voyage(voyage)
                .utilisateur(utilisateur)
                .place(place)
                .numeroPlace(place.getNumeroPlace())
                .statut(Reservation.STATUT_EN_ATTENTE)
                .build();
        reservation = reservationRepository.save(reservation);

        // Si toutes les places sont prises, on marque le voyage comme complet.
        long dispo = placeRepository.countByVoyageIdAndStatut(voyage.getId(), "disponible");
        if (dispo == 0 && "actif".equals(voyage.getStatut())) {
            voyage.setStatut("complet");
        }

        return reservation;
    }

    /** Libère une place (annulation avant paiement, ou côté admin). */
    @Transactional
    public void libererPlace(UUID placeId) {
        Place place = placeRepository.findByIdForUpdate(placeId)
                .orElseThrow(() -> ApiException.notFound("Place introuvable."));

        place.setStatut("disponible");
        placeRepository.save(place);

        reservationRepository.findByPlaceId(placeId).ifPresent(r -> {
            r.setStatut(Reservation.STATUT_ANNULEE);
            reservationRepository.save(r);
        });

        Voyage voyage = place.getVoyage();
        if ("complet".equals(voyage.getStatut())) {
            voyage.setStatut("actif");
        }
    }
}
