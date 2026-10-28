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

    /**
     * Reçu d'une réservation. L'accès est contrôlé AVANT toute génération : un utilisateur qui
     * n'est ni propriétaire, ni Président de la coopérative, ni ADMIN ne peut pas provoquer la
     * création d'un reçu pour la réservation d'un autre.
     */
    @Transactional
    public RecuDTO findByReservation(UUID reservationId, UUID utilisateurId, Utilisateur.Role role) {
        java.util.Optional<Recu> existant = recuRepository.findByReservationIdWithDetails(reservationId);
        if (existant.isPresent()) {
            verifierAcces(existant.get().getPaiement().getReservation(), utilisateurId, role);
            return toDto(existant.get());
        }
        Paiement paiement = paiementRepository.findByReservationIdWithDetails(reservationId)
                .orElseThrow(() -> ApiException.notFound("Aucun reçu pour cette réservation."));
        verifierAcces(paiement.getReservation(), utilisateurId, role);
        // Pas de reçu tant que le paiement n'est pas confirmé ET la réservation validée.
        Recu genere = ensureRecuForPaiement(paiement);
        if (genere == null) {
            throw ApiException.notFound("Aucun reçu pour cette réservation.");
        }
        return toDto(genere);
    }

    /** ADMIN ; VOYAGEUR propriétaire de la réservation ; PRESIDENT de la coopérative du voyage. */
    private void verifierAcces(Reservation r, UUID utilisateurId, Utilisateur.Role role) {
        if (role == Utilisateur.Role.ADMIN) return;
        if (role == Utilisateur.Role.VOYAGEUR) {
            if (r != null && r.getUtilisateur() != null && r.getUtilisateur().getId().equals(utilisateurId)) return;
        } else if (role == Utilisateur.Role.PRESIDENT) {
            Voyage v = r != null ? r.getVoyage() : null;
            if (v != null && v.getCooperative() != null && v.getCooperative().getPresident() != null
                    && v.getCooperative().getPresident().getId().equals(utilisateurId)) return;
        }
        throw ApiException.forbidden("Accès refusé à ce reçu.");
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
        // Le reçu n'existe que si le paiement est confirmé (« Réussi ») ET la réservation « Validée ».
        if (!Paiement.STATUT_REUSSI.equals(paiement.getStatut())) {
            return null;
        }
        Reservation reservation = paiement.getReservation();
        if (reservation == null || !Reservation.STATUT_VALIDEE.equals(reservation.getStatut())) {
            return null;
        }
        return recuRepository.findByPaiementId(paiement.getId()).orElseGet(() -> saveRecuWithRetry(paiement));
    }

    @Transactional
    public RecuDTO create(RecuIO.CreateRecuRequest req, UUID utilisateurId, Utilisateur.Role role) {
        if (req.getPaiementId() == null || req.getPaiementId().isBlank()) {
            throw ApiException.badRequest("paiement_id requis.");
        }
        Paiement paiement = paiementRepository.findById(UUID.fromString(req.getPaiementId()))
                .orElseThrow(() -> ApiException.notFound("Paiement introuvable."));
        // Le reçu ne peut être créé que par le propriétaire de la réservation, le Président de
        // la coopérative concernée ou un ADMIN.
        verifierAcces(paiement.getReservation(), utilisateurId, role);

        if (!Paiement.STATUT_REUSSI.equals(paiement.getStatut())) {
            throw ApiException.conflict("Le reçu sera disponible une fois le paiement confirmé par la coopérative.");
        }
        if (paiement.getReservation() == null
                || !Reservation.STATUT_VALIDEE.equals(paiement.getReservation().getStatut())) {
            throw ApiException.conflict("Le reçu sera disponible une fois la réservation validée par la coopérative.");
        }

        if (recuRepository.findByPaiementId(paiement.getId()).isPresent()) {
            throw ApiException.conflict("Un reçu existe déjà pour ce paiement.");
        }

        return toDto(saveRecuWithRetry(paiement));
    }

    /**
     * Page publique /verify/:token — scannée sans authentification. Elle n'expose que les
     * informations indispensables au contrôle d'un billet (voir toPublicDto) : pas d'identifiants
     * internes, pas de jeton, pas de téléphone ni de nom complet du voyageur.
     */
    @Transactional(readOnly = true)
    public RecuIO.VerifyResponse verifyByToken(String token) {
        Recu recu = recuRepository.findByTokenWithDetails(token)
                .orElseThrow(() -> ApiException.notFound("Reçu introuvable ou lien invalide."));
        boolean alreadyUsed = recu.getCheckedAt() != null;
        return RecuIO.VerifyResponse.builder()
                .recu(toPublicDto(toDto(recu)))
                .alreadyUsed(alreadyUsed)
                .build();
    }

    /**
     * Validation de l'embarquement : réservée à un utilisateur authentifié et autorisé
     * (ADMIN, ou Président de la coopérative du voyage). L'agent enregistré est l'utilisateur
     * authentifié, jamais une valeur envoyée par le client. Le reçu est verrouillé pendant
     * l'opération : deux validations simultanées ne peuvent pas réussir toutes les deux.
     */
    @Transactional
    public RecuIO.CheckinResponse checkin(String token, UUID utilisateurId, Utilisateur.Role role, String agentNom) {
        Recu recu = recuRepository.findByTokenForUpdate(token)
                .orElseThrow(() -> ApiException.notFound("Reçu introuvable ou lien invalide."));
        Reservation reservation = recu.getPaiement().getReservation();

        boolean autorise = role == Utilisateur.Role.ADMIN;
        if (!autorise && role == Utilisateur.Role.PRESIDENT) {
            Voyage v = reservation.getVoyage();
            autorise = v != null && v.getCooperative() != null && v.getCooperative().getPresident() != null
                    && v.getCooperative().getPresident().getId().equals(utilisateurId);
        }
        if (!autorise) {
            throw ApiException.forbidden("Seul le Président de la coopérative concernée peut valider l'embarquement.");
        }
        if (!Reservation.STATUT_VALIDEE.equals(reservation.getStatut())) {
            throw ApiException.conflict("Cette réservation n'est pas validée : embarquement impossible.");
        }
        if (recu.getCheckedAt() != null) {
            throw ApiException.conflict("Ce billet a déjà été utilisé pour l'embarquement le "
                    + recu.getCheckedAt() + ".");
        }

        recu.setCheckedAt(OffsetDateTime.now());
        recu.setCheckedBy(agentNom);
        recu.setStatut("Embarquée");
        recuRepository.save(recu);

        return RecuIO.CheckinResponse.builder().recu(toPublicDto(toDto(recu))).build();
    }

    /** Vue publique minimale d'un reçu (contrôle de billet). */
    static RecuDTO toPublicDto(RecuDTO d) {
        return RecuDTO.builder()
                .numeroRecu(d.getNumeroRecu())
                .cooperativeNom(d.getCooperativeNom())
                .voyageurNom(masquerNom(d.getVoyageurNom()))
                .voyageurTelephone(masquerTelephone(d.getVoyageurTelephone()))
                .villeDepart(d.getVilleDepart())
                .villeArrivee(d.getVilleArrivee())
                .dateDepart(d.getDateDepart())
                .heureDepart(d.getHeureDepart())
                .numeroPlace(d.getNumeroPlace())
                .dateReservation(d.getDateReservation())
                .statut(d.getStatut())
                .checkedAt(d.getCheckedAt())
                .build();
    }

    /** « Rakoto Ada » -> « R•••• A•• » : permet à l'agent de reconnaître le voyageur sans exposer son nom. */
    static String masquerNom(String nom) {
        if (nom == null || nom.isBlank()) return null;
        StringBuilder sb = new StringBuilder();
        for (String mot : nom.trim().split("\\s+")) {
            if (sb.length() > 0) sb.append(' ');
            sb.append(mot.charAt(0));
            for (int i = 1; i < Math.min(mot.length(), 5); i++) sb.append('•');
        }
        return sb.toString();
    }

    /** Ne garde que les 2 derniers chiffres. */
    static String masquerTelephone(String telephone) {
        if (telephone == null) return null;
        String chiffres = telephone.replaceAll("[^0-9]", "");
        if (chiffres.length() < 4) return null;
        return "••• •• ••• " + chiffres.substring(chiffres.length() - 2);
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
