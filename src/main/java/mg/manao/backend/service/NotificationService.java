package mg.manao.backend.service;

import lombok.RequiredArgsConstructor;
import mg.manao.backend.dto.NotificationIO;
import mg.manao.backend.entity.Notification;
import mg.manao.backend.entity.Reservation;
import mg.manao.backend.entity.Utilisateur;
import mg.manao.backend.exception.ApiException;
import mg.manao.backend.repository.NotificationRepository;
import mg.manao.backend.repository.ReservationRepository;
import mg.manao.backend.repository.UtilisateurRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UtilisateurRepository utilisateurRepository;
    private final ReservationRepository reservationRepository;

    public static NotificationIO.NotificationDTO toDto(Notification n) {
        return NotificationIO.NotificationDTO.builder()
                .id(n.getId().toString())
                .utilisateurId(n.getUtilisateur() != null ? n.getUtilisateur().getId().toString() : null)
                .reservationId(n.getReservation() != null ? n.getReservation().getId().toString() : null)
                .type(n.getType())
                .message(n.getMessage())
                .statut(n.getStatut())
                .dateEnvoi(n.getDateEnvoi())
                .build();
    }

    public List<NotificationIO.NotificationDTO> findAll() {
        return notificationRepository.findAllByOrderByDateEnvoiDesc().stream().map(NotificationService::toDto).toList();
    }

    @Transactional
    public NotificationIO.NotificationDTO create(NotificationIO.NotificationRequest req) {
        Notification.NotificationBuilder builder = Notification.builder()
                .type(req.getType() != null ? req.getType() : "Système")
                .message(req.getMessage())
                .statut((req.getStatut() == null || req.getStatut().isBlank()) ? "Envoyé" : req.getStatut());

        if (req.getUtilisateurId() != null && !req.getUtilisateurId().isBlank()) {
            Utilisateur u = utilisateurRepository.findById(UUID.fromString(req.getUtilisateurId()))
                    .orElseThrow(() -> ApiException.badRequest("Utilisateur introuvable."));
            builder.utilisateur(u);
        }
        if (req.getReservationId() != null && !req.getReservationId().isBlank()) {
            Reservation r = reservationRepository.findById(UUID.fromString(req.getReservationId()))
                    .orElseThrow(() -> ApiException.badRequest("Réservation introuvable."));
            builder.reservation(r);
        }

        return toDto(notificationRepository.save(builder.build()));
    }

    @Transactional
    public void delete(UUID id) {
        if (!notificationRepository.existsById(id)) {
            throw ApiException.notFound("Notification introuvable.");
        }
        notificationRepository.deleteById(id);
    }
}
