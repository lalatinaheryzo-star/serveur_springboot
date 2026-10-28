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

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PlaceService {

    private static final ZoneId FUSEAU_MADAGASCAR = ZoneId.of("Indian/Antananarivo");
    /** Statuts pour lesquels une réservation occupe réellement une place. */
    static final List<String> STATUTS_ACTIFS = List.of(Reservation.STATUT_EN_ATTENTE, Reservation.STATUT_VALIDEE);

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
     * concurrent — évite la survente d'un siège). Le contrôle du statut se fait
     * APRÈS l'obtention du verrou : la seconde requête voit la place déjà prise (409).
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
        if (voyageDejaParti(voyage)) {
            throw ApiException.conflict("Ce voyage est déjà parti.");
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

    private static boolean voyageDejaParti(Voyage voyage) {
        if (voyage.getDateDepart() == null) return false;
        LocalDateTime depart = LocalDateTime.of(voyage.getDateDepart(),
                voyage.getHeureDepart() != null ? voyage.getHeureDepart() : java.time.LocalTime.MIDNIGHT);
        return depart.isBefore(LocalDateTime.now(FUSEAU_MADAGASCAR));
    }

    /**
     * Libère une place (annulation avant paiement, ou côté admin).
     * Autorisé : ADMIN ; le VOYAGEUR propriétaire de la réservation active liée à cette place
     * (tant qu'elle est « En attente ») ; le PRESIDENT de la coopérative qui exploite le voyage.
     * Tout autre cas -> 403, sans aucune modification.
     */
    @Transactional
    public void libererPlace(UUID placeId, UUID utilisateurId, Utilisateur.Role role) {
        Place place = placeRepository.findByIdForUpdate(placeId)
                .orElseThrow(() -> ApiException.notFound("Place introuvable."));

        Optional<Reservation> reservation = reservationRepository
                .findByPlaceIdAndStatutIn(placeId, STATUTS_ACTIFS).stream().findFirst();
        verifierDroitDeLiberer(place, reservation.orElse(null), utilisateurId, role);

        if (reservation.isPresent()) {
            Reservation r = reservation.get();
            r.setStatut(Reservation.STATUT_ANNULEE);
            rendrePlaceDisponible(r); // libère la place, détache le lien, rouvre le voyage complet
            reservationRepository.save(r);
        } else {
            place.setStatut("disponible");
            placeRepository.save(place);
            rouvrirVoyageSiComplet(place);
        }
    }

    /**
     * Remet à « disponible » la place liée à une réservation qui cesse d'être active (annulation,
     * refus, suppression) et DÉTACHE le lien réservation -> place (le numéro de place reste
     * conservé dans la réservation). Ainsi la place peut être réservée de nouveau sans laisser
     * deux réservations pointer vers la même place. Verrou pessimiste sur la place.
     * À appeler dans une transaction.
     */
    @Transactional
    public void rendrePlaceDisponible(Reservation reservation) {
        if (reservation.getPlace() == null) return;
        Place place = placeRepository.findByIdForUpdate(reservation.getPlace().getId()).orElse(null);
        if (place != null) {
            place.setStatut("disponible");
            placeRepository.save(place);
            rouvrirVoyageSiComplet(place);
        }
        reservation.setPlace(null);
    }

    private void rouvrirVoyageSiComplet(Place place) {
        Voyage voyage = place.getVoyage();
        if (voyage != null && "complet".equals(voyage.getStatut())) {
            voyage.setStatut("actif");
        }
    }

    private void verifierDroitDeLiberer(Place place, Reservation reservation, UUID utilisateurId, Utilisateur.Role role) {
        if (role == Utilisateur.Role.ADMIN) return;
        if (role == Utilisateur.Role.VOYAGEUR) {
            if (reservation != null && reservation.getUtilisateur() != null
                    && reservation.getUtilisateur().getId().equals(utilisateurId)) {
                // Un voyageur n'annule que SA réservation encore « En attente » (annulation avant
                // validation). Une réservation validée par le Président ne se libère plus seul.
                if (Reservation.STATUT_EN_ATTENTE.equals(reservation.getStatut())) return;
                throw ApiException.forbidden("Cette réservation est déjà traitée : contactez la coopérative pour l'annuler.");
            }
        } else if (role == Utilisateur.Role.PRESIDENT) {
            Voyage voyage = place.getVoyage();
            if (voyage != null && voyage.getCooperative() != null
                    && voyage.getCooperative().getPresident() != null
                    && voyage.getCooperative().getPresident().getId().equals(utilisateurId)) {
                return;
            }
        }
        throw ApiException.forbidden("Vous n'êtes pas autorisé à libérer cette place.");
    }
}
