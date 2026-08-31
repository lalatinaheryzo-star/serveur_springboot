package mg.manao.backend.controller;

import lombok.RequiredArgsConstructor;
import mg.manao.backend.dto.NotificationIO;
import mg.manao.backend.service.NotificationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/** ADMIN uniquement (voir SecurityConfig) — pages/Notifications.jsx est un écran admin en lecture seule. */
@RestController
@RequestMapping("/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    public List<NotificationIO.NotificationDTO> findAll() {
        return notificationService.findAll();
    }

    @PostMapping
    public ResponseEntity<NotificationIO.NotificationDTO> create(@RequestBody NotificationIO.NotificationRequest req) {
        return ResponseEntity.status(201).body(notificationService.create(req));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        notificationService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
