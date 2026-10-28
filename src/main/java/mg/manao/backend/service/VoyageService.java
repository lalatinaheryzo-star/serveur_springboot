package mg.manao.backend.service;

import lombok.RequiredArgsConstructor;
import mg.manao.backend.dto.VoyageDTO;
import mg.manao.backend.dto.VoyageRequest;
import mg.manao.backend.entity.Cooperative;
import mg.manao.backend.entity.Place;
import mg.manao.backend.entity.Voyage;
import mg.manao.backend.exception.ApiException;
import mg.manao.backend.repository.PlaceRepository;
import mg.manao.backend.repository.VoyageRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class VoyageService {

    private final VoyageRepository voyageRepository;
    private final PlaceRepository placeRepository;

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

    public VoyageDTO toDto(Voyage v) {
        List<Place> places = placeRepository.findByVoyageIdOrderByNumeroPlaceAsc(v.getId());
        return toDto(v, places);
    }

    /** Variante sans requête DB : utilisée par findAll() quand les places de TOUS les
     *  voyages ont déjà été chargées en une seule requête groupée (voir findAll()). */
    private VoyageDTO toDto(Voyage v, List<Place> places) {
        int total = places.isEmpty() ? v.getCapacite() : places.size();
        int dispo = places.isEmpty()
                ? v.getCapacite()
                : (int) places.stream().filter(p -> "disponible".equals(p.getStatut())).count();

        return VoyageDTO.builder()
                .id(v.getId().toString())
                .villeDepart(v.getVilleDepart())
                .villeArrivee(v.getVilleArrivee())
                .dateDepart(v.getDateDepart() != null ? v.getDateDepart().toString() : null)
                .heureDepart(v.getHeureDepart() != null ? v.getHeureDepart().format(TIME_FMT) : null)
                .prix(v.getPrix())
                .statut(v.getStatut())
                .cooperativeId(v.getCooperative() != null ? v.getCooperative().getId().toString() : null)
                .cooperativeNom(v.getCooperative() != null ? v.getCooperative().getNom() : null)
                .vehiculeNom(v.getVehiculeNom())
                .capacite(v.getCapacite())
                .placesDisponibles(dispo)
                .placesTotal(total)
                .build();
    }

    @Transactional(readOnly = true)
    public List<VoyageDTO> findAll() {
        List<Voyage> voyages = voyageRepository.findAllOrdered();
        if (voyages.isEmpty()) return List.of();

        // Une seule requête pour les places de TOUS les voyages (au lieu d'une
        // requête par voyage dans toDto()) : élimine le N+1 qui ralentissait
        // cette liste, affichée sur les 3 espaces (Admin, Président, Voyageur).
        List<UUID> ids = voyages.stream().map(Voyage::getId).toList();
        Map<UUID, List<Place>> placesByVoyage = placeRepository.findByVoyageIdIn(ids).stream()
                .collect(Collectors.groupingBy(p -> p.getVoyage().getId()));

        return voyages.stream()
                .map(v -> toDto(v, placesByVoyage.getOrDefault(v.getId(), List.of())))
                .toList();
    }

    public Voyage getEntity(UUID id) {
        return voyageRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Voyage introuvable."));
    }

    @Transactional(readOnly = true)
    public VoyageDTO findById(UUID id) {
        return toDto(getEntity(id));
    }

    @Transactional
    public VoyageDTO create(VoyageRequest req, Cooperative coop) {
        Voyage v = new Voyage();
        applyRequest(v, req);
        v.setCooperative(coop);
        v = voyageRepository.save(v);
        generatePlaces(v);
        return toDto(v);
    }

    @Transactional
    public VoyageDTO update(UUID id, VoyageRequest req, Cooperative coop) {
        Voyage v = getEntity(id);
        if (v.getCooperative() == null || !v.getCooperative().getId().equals(coop.getId())) {
            throw ApiException.forbidden("Ce voyage n'appartient pas à votre coopérative.");
        }
        int previousCapacite = v.getCapacite();
        int nbPlaces = placeRepository.findByVoyageIdOrderByNumeroPlaceAsc(v.getId()).size();
        applyRequest(v, req);
        if (v.getCapacite() < nbPlaces) {
            // Les places existantes ne sont jamais supprimées (elles peuvent être réservées).
            throw ApiException.conflict("La capacité ne peut pas être réduite sous le nombre de places existantes (" + nbPlaces + ").");
        }
        v = voyageRepository.save(v);
        // Si la capacité augmente, on ajoute les places manquantes (les places
        // existantes / déjà réservées ne sont jamais touchées).
        if (v.getCapacite() != null && v.getCapacite() > previousCapacite) {
            generateMissingPlaces(v);
        }
        return toDto(v);
    }

    @Transactional
    public void delete(UUID id, Cooperative coop) {
        Voyage v = getEntity(id);
        if (v.getCooperative() == null || !v.getCooperative().getId().equals(coop.getId())) {
            throw ApiException.forbidden("Ce voyage n'appartient pas à votre coopérative.");
        }
        voyageRepository.delete(v);
    }

    static final java.math.BigDecimal PRIX_MAX = new java.math.BigDecimal("10000000"); // 10 M Ar
    static final int CAPACITE_MAX = 100;
    static final int CAPACITE_DEFAUT = 18;
    static final int TEXTE_MAX = 100;
    static final java.util.Set<String> STATUTS = java.util.Set.of("actif", "complet", "annulé");
    private static final java.time.ZoneId FUSEAU = java.time.ZoneId.of("Indian/Antananarivo");

    /**
     * Valide puis applique la requête. `existant` = voyage déjà en base (modification) ou null
     * (création). Règles : villes non vides, ≤ 100 caractères et différentes ; prix entre 0 et
     * 10 000 000 Ar ; capacité 1..100 (défaut 18 si absente) ; statut parmi actif/complet/annulé ;
     * date non passée (à la création, ou si la date change à la modification).
     */
    private void applyRequest(Voyage v, VoyageRequest req) {
        if (req.getVilleDepart() == null || req.getVilleDepart().isBlank()) {
            throw ApiException.badRequest("Ville de départ requise.");
        }
        if (req.getVilleArrivee() == null || req.getVilleArrivee().isBlank()) {
            throw ApiException.badRequest("Ville d'arrivée requise.");
        }
        if (req.getDateDepart() == null || req.getDateDepart().isBlank()) {
            throw ApiException.badRequest("Date de départ requise.");
        }
        String depart = req.getVilleDepart().trim();
        String arrivee = req.getVilleArrivee().trim();
        if (depart.length() > TEXTE_MAX || arrivee.length() > TEXTE_MAX) {
            throw ApiException.badRequest("Nom de ville trop long.");
        }
        if (depart.equalsIgnoreCase(arrivee)) {
            throw ApiException.badRequest("La ville de départ et la ville d'arrivée doivent être différentes.");
        }

        LocalDate date;
        try {
            date = LocalDate.parse(req.getDateDepart());
        } catch (Exception e) {
            throw ApiException.badRequest("Format de date de départ invalide (attendu yyyy-MM-dd).");
        }
        boolean dateChangee = v.getDateDepart() == null || !v.getDateDepart().equals(date);
        if (dateChangee && date.isBefore(LocalDate.now(FUSEAU))) {
            throw ApiException.badRequest("La date de départ ne peut pas être dans le passé.");
        }
        LocalTime heure;
        try {
            String h = (req.getHeureDepart() == null || req.getHeureDepart().isBlank()) ? "00:00" : req.getHeureDepart();
            heure = LocalTime.parse(h.length() == 5 ? h + ":00" : h);
        } catch (Exception e) {
            throw ApiException.badRequest("Format d'heure de départ invalide (attendu HH:mm).");
        }

        java.math.BigDecimal prix = req.getPrix() != null ? req.getPrix() : java.math.BigDecimal.ZERO;
        if (prix.signum() < 0 || prix.compareTo(PRIX_MAX) > 0) {
            throw ApiException.badRequest("Prix invalide (entre 0 et 10 000 000 Ar).");
        }

        String statut = (req.getStatut() == null || req.getStatut().isBlank()) ? "actif" : req.getStatut().trim();
        if (!STATUTS.contains(statut)) {
            throw ApiException.badRequest("Statut de voyage invalide (actif, complet ou annulé).");
        }

        int capacite = CAPACITE_DEFAUT;
        if (req.getCapacite() != null) {
            if (req.getCapacite() < 1 || req.getCapacite() > CAPACITE_MAX) {
                throw ApiException.badRequest("Capacité invalide (entre 1 et " + CAPACITE_MAX + ").");
            }
            capacite = req.getCapacite();
        }
        String vehicule = (req.getVehiculeNom() == null || req.getVehiculeNom().isBlank()) ? "Sprinter" : req.getVehiculeNom().trim();
        if (vehicule.length() > TEXTE_MAX) {
            throw ApiException.badRequest("Nom de véhicule trop long.");
        }

        v.setVilleDepart(depart);
        v.setVilleArrivee(arrivee);
        v.setDateDepart(date);
        v.setHeureDepart(heure);
        v.setPrix(prix);
        v.setStatut(statut);
        v.setVehiculeNom(vehicule);
        v.setCapacite(capacite);
    }

    private void generatePlaces(Voyage v) {
        List<Place> places = new ArrayList<>();
        for (int i = 1; i <= v.getCapacite(); i++) {
            places.add(Place.builder().voyage(v).numeroPlace(i).statut("disponible").build());
        }
        placeRepository.saveAll(places);
    }

    private void generateMissingPlaces(Voyage v) {
        int existing = placeRepository.findByVoyageIdOrderByNumeroPlaceAsc(v.getId()).size();
        List<Place> places = new ArrayList<>();
        for (int i = existing + 1; i <= v.getCapacite(); i++) {
            places.add(Place.builder().voyage(v).numeroPlace(i).statut("disponible").build());
        }
        if (!places.isEmpty()) placeRepository.saveAll(places);
    }
}
