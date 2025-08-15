package module.notification.controllers;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import module.notification.dto.BulkNotificationDto;
import module.notification.dto.NotificationDto;
import module.notification.dto.NotificationRequestDto;
import module.notification.services.servicesImpl.NotificationService;
import org.hibernate.annotations.Parameter;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class NotificationController {

    private final NotificationService notificationService;

    @PostMapping("/send")
    @Operation(summary = "Envoyer une notification")
    public CompletableFuture<ResponseEntity<NotificationDto>> sendNotification(
            @Valid @RequestBody NotificationRequestDto request) {

        return notificationService.sendNotification(request)
                .thenApply(ResponseEntity::ok);
    }

    @PostMapping("/send-bulk")
    @Operation(summary = "Envoyer des notifications en masse")
    public CompletableFuture<ResponseEntity<List<NotificationDto>>> sendBulkNotifications(
            @Valid @RequestBody BulkNotificationDto request) {

        return notificationService.sendBulkNotifications(request)
                .thenApply(ResponseEntity::ok);
    }

    @GetMapping("/recipient/{recipientId}")
    @Operation(summary = "Récupérer les notifications d'un destinataire")
    public ResponseEntity<Page<NotificationDto>> getNotificationsByRecipient(
            @PathVariable @Parameter(description = "ID du destinataire") String recipientId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Pageable pageable = PageRequest.of(page, size);
        Page<NotificationDto> notifications = notificationService.getNotificationsByRecipient(recipientId, pageable);

        return ResponseEntity.ok(notifications);
    }

    @PutMapping("/{notificationId}/read")
    @Operation(summary = "Marquer une notification comme lue")
    public ResponseEntity<Void> markAsRead(
            @PathVariable @Parameter(description = "ID de la notification") Long notificationId,
            @RequestParam @Parameter(description = "ID du destinataire") String recipientId) {

        notificationService.markAsRead(notificationId, recipientId);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/recipient/{recipientId}/unread-count")
    @Operation(summary = "Obtenir le nombre de notifications non lues")
    public ResponseEntity<Long> getUnreadCount(
            @PathVariable @Parameter(description = "ID du destinataire") String recipientId) {

        Long count = notificationService.getUnreadCount(recipientId);
        return ResponseEntity.ok(count);
    }

    @GetMapping("/recipient/{recipientId}/unread")
    @Operation(summary = "Récupérer les notifications non lues")
    public ResponseEntity<List<NotificationDto>> getUnreadNotifications(
            @PathVariable @Parameter(description = "ID du destinataire") String recipientId) {

        List<NotificationDto> notifications = notificationService.getUnreadNotifications(recipientId);
        return ResponseEntity.ok(notifications);
    }
}
