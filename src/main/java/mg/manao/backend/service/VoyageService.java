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
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class VoyageService {

    private final VoyageRepository voyageRepository;
    private final PlaceRepository placeRepository;

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

    public VoyageDTO toDto(Voyage v) {
        List<Place> places = placeRepository.findByVoyageIdOrderByNumeroPlaceAsc(v.getId());
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
        return voyageRepository.findAllOrdered().stream().map(this::toDto).toList();
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
        applyRequest(v, req);
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

        v.setVilleDepart(req.getVilleDepart().trim());
        v.setVilleArrivee(req.getVilleArrivee().trim());
        try {
            v.setDateDepart(LocalDate.parse(req.getDateDepart()));
        } catch (Exception e) {
            throw ApiException.badRequest("Format de date de départ invalide (attendu yyyy-MM-dd).");
        }
        try {
            String heure = (req.getHeureDepart() == null || req.getHeureDepart().isBlank()) ? "00:00" : req.getHeureDepart();
            v.setHeureDepart(LocalTime.parse(heure.length() == 5 ? heure + ":00" : heure));
        } catch (Exception e) {
            throw ApiException.badRequest("Format d'heure de départ invalide (attendu HH:mm).");
        }
        v.setPrix(req.getPrix() != null ? req.getPrix() : java.math.BigDecimal.ZERO);
        v.setStatut((req.getStatut() == null || req.getStatut().isBlank()) ? "actif" : req.getStatut());
        v.setVehiculeNom((req.getVehiculeNom() == null || req.getVehiculeNom().isBlank()) ? "Sprinter" : req.getVehiculeNom());
        v.setCapacite(req.getCapacite() != null && req.getCapacite() > 0 ? req.getCapacite() : 18);
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
