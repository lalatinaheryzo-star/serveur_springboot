package mg.manao.backend.service;

import lombok.RequiredArgsConstructor;
import mg.manao.backend.dto.RecuDTO;
import mg.manao.backend.dto.RecuIO;
import mg.manao.backend.entity.Paiement;
import mg.manao.backend.entity.Recu;
import mg.manao.backend.entity.Reservation;
import mg.manao.backend.entity.Utilisateur;
import mg.manao.backend.entity.Voyage;
import mg.manao.backend.exception.ApiException;
import mg.manao.backend.repository.PaiementRepository;
import mg.manao.backend.repository.RecuRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.LocalDate;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RecuService {

    private final RecuRepository recuRepository;
    private final PaiementRepository paiementRepository;
    private final PdfService pdfService;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final SecureRandom RANDOM = new SecureRandom();

    public RecuDTO toDto(Recu recu) {
        Paiement p = recu.getPaiement();
        Reservation r = p.getReservation();
        Voyage v = r.getVoyage();
        Utilisateur u = r.getUtilisateur();

        return RecuDTO.builder()
                .id(recu.getId().toString())
                .paiementId(p.getId().toString())
                .reservationId(r.getId().toString())
                .numeroRecu(recu.getNumeroRecu())
                .token(recu.getToken())
                .verifyUrl(frontendUrl.replaceAll("/$", "") + "/verify/" + recu.getToken())
                .cooperativeNom(v != null && v.getCooperative() != null ? v.getCooperative().getNom() : null)
                .cooperativeTelephone(v != null && v.getCooperative() != null ? v.getCooperative().getTelephone() : null)
                .voyageurNom(u != null ? (u.getNom() + " " + u.getPrenom()) : null)
                .voyageurTelephone(u != null ? u.getTelephone() : null)
                .villeDepart(v != null ? v.getVilleDepart() : null)
                .villeArrivee(v != null ? v.getVilleArrivee() : null)
                .dateDepart(v != null && v.getDateDepart() != null ? v.getDateDepart().toString() : null)
                .heureDepart(v != null && v.getHeureDepart() != null ? v.getHeureDepart().format(TIME_FMT) : null)
                .numeroPlace(r.getNumeroPlace())
                .dateReservation(r.getDateReservation())
                .statut(recu.getStatut())
                .statutReservationLabel(r.getStatut())
                .modePaiement(p.getModePaiement())
                .montant(p.getMontant())
                .dateGeneration(recu.getDateGeneration())
                .checkedAt(recu.getCheckedAt())
                .checkedBy(recu.getCheckedBy())
                .utilisateurIdInterne(u != null ? u.getId().toString() : null)
                .cooperativePresidentIdInterne(
                        v != null && v.getCooperative() != null && v.getCooperative().getPresident() != null
                                ? v.getCooperative().getPresident().getId().toString()
                                : null)
                .build();
    }

    @Transactional(readOnly = true)
    public List<RecuDTO> findAll() {
        return recuRepository.findAllByOrderByDateGenerationDesc().stream().map(this::toDto).toList();
    }

    public Recu getEntity(UUID id) {
        return recuRepository.findByIdWithDetails(id)
                .orElseThrow(() -> ApiException.notFound("Reçu introuvable."));
    }

    @Transactional(readOnly = true)
    public RecuDTO findById(UUID id) {
        return toDto(getEntity(id));
    }

    @Transactional
    public RecuDTO findByReservation(UUID reservationId) {
        Recu recu = recuRepository.findByReservationIdWithDetails(reservationId)
                .orElseGet(() -> {
                    Paiement paiement = paiementRepository.findByReservationIdWithDetails(reservationId)
                            .filter(p -> Paiement.STATUT_REUSSI.equals(p.getStatut()))
                            .orElseThrow(() -> ApiException.notFound("Aucun reçu pour cette réservation."));
                    return ensureRecuForPaiement(paiement);
                });
        return toDto(recu);
    }

    /**
     * Génère le reçu d'un paiement "Réussi" s'il n'existe pas encore (idempotent).
     * Appelé (a) en filet de sécurité par findByReservation, pour qu'un voyageur
     * qui a bien payé ne tombe jamais sur un 404 même si la création initiale a
     * échoué ou a été sautée, et (b) par PaiementService.updateStatut, pour
     * générer le reçu dès qu'un Président valide un paiement en espèces — sans
     * attendre que quelqu'un ouvre la page du reçu.
     */
    @Transactional
    public Recu ensureRecuForPaiement(Paiement paiement) {
        if (!Paiement.STATUT_REUSSI.equals(paiement.getStatut())) {
            return null;
        }
        return recuRepository.findByPaiementId(paiement.getId()).orElseGet(() -> saveRecuWithRetry(paiement));
    }

    @Transactional
    public RecuDTO create(RecuIO.CreateRecuRequest req) {
        if (req.getPaiementId() == null || req.getPaiementId().isBlank()) {
            throw ApiException.badRequest("paiement_id requis.");
        }
        Paiement paiement = paiementRepository.findById(UUID.fromString(req.getPaiementId()))
                .orElseThrow(() -> ApiException.notFound("Paiement introuvable."));

        if (!Paiement.STATUT_REUSSI.equals(paiement.getStatut())) {
            throw ApiException.badRequest("Un reçu ne peut être généré que pour un paiement réussi.");
        }

        if (recuRepository.findByPaiementId(paiement.getId()).isPresent()) {
            throw ApiException.conflict("Un reçu existe déjà pour ce paiement.");
        }

        return toDto(saveRecuWithRetry(paiement));
    }

    /** Page publique /verify/:token — scannée par un agent de gare, sans authentification. */
    public RecuIO.VerifyResponse verifyByToken(String token) {
        Recu recu = recuRepository.findByTokenWithDetails(token)
                .orElseThrow(() -> ApiException.notFound("Reçu introuvable ou lien invalide."));
        boolean alreadyUsed = recu.getCheckedAt() != null;
        return RecuIO.VerifyResponse.builder()
                .recu(toDto(recu))
                .alreadyUsed(alreadyUsed)
                .build();
    }

    @Transactional
    public RecuIO.CheckinResponse checkin(String token, RecuIO.CheckinRequest req) {
        Recu recu = recuRepository.findByTokenWithDetails(token)
                .orElseThrow(() -> ApiException.notFound("Reçu introuvable ou lien invalide."));

        if (recu.getCheckedAt() != null) {
            throw ApiException.conflict("Ce billet a déjà été utilisé pour l'embarquement le "
                    + recu.getCheckedAt() + ".");
        }

        recu.setCheckedAt(OffsetDateTime.now());
        recu.setCheckedBy(req.getCheckedBy());
        recu.setStatut("Embarquée");
        recuRepository.save(recu);

        return RecuIO.CheckinResponse.builder().recu(toDto(recu)).build();
    }

    @Transactional
    public void delete(UUID id) {
        if (!recuRepository.existsById(id)) {
            throw ApiException.notFound("Reçu introuvable.");
        }
        recuRepository.deleteById(id);
    }

    @Transactional(readOnly = true)
    public byte[] downloadPdf(UUID id) {
        return generatePdf(findById(id));
    }

    @Transactional(readOnly = true)
    public byte[] generatePdf(RecuDTO dto) {
        return pdfService.generateRecuPdf(dto);
    }

    /**
     * Construit un nouveau reçu pour ce paiement avec un numéro unique
     * (basé sur le plus grand numéro déjà attribué — voir generateNumeroRecu).
     * Avec cette base de calcul, une collision sur numero_recu ne devrait
     * plus se produire en usage normal (un seul Président validant un
     * paiement à la fois) ; elle n'était possible qu'avec l'ancien calcul
     * basé sur recuRepository.count().
     */
    private Recu saveRecuWithRetry(Paiement paiement) {
        Recu recu = Recu.builder()
                .paiement(paiement)
                .numeroRecu(generateNumeroRecu())
                .token(generateToken())
                .statut("Confirmée")
                .build();
        return recuRepository.save(recu);
    }

    /**
     * Prochain numéro de reçu disponible pour l'année en cours, basé sur le
     * plus grand numéro déjà attribué (et non sur recuRepository.count(),
     * qui peut être décorrélé du vrai maximum : ligne supprimée, données de
     * démo régénérées au démarrage, etc. — ce décalage provoquait des
     * violations répétées de la contrainte unique recus_numero_recu_key).
     */
    private String generateNumeroRecu() {
        String year = String.valueOf(LocalDate.now().getYear());
        String prefix = "REC-" + year + "-";
        long next = recuRepository.findTopByNumeroRecuStartingWithOrderByNumeroRecuDesc(prefix)
                .map(r -> {
                    String suffix = r.getNumeroRecu().substring(prefix.length());
                    try {
                        return Long.parseLong(suffix) + 1;
                    } catch (NumberFormatException e) {
                        return recuRepository.count() + 1;
                    }
                })
                .orElse(1L);
        return prefix + String.format("%06d", next);
    }

    private String generateToken() {
        byte[] bytes = new byte[24];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
