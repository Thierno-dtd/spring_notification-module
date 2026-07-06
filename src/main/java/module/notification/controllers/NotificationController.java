package module.notification.controllers;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import module.notification.dto.BulkNotificationDto;
import module.notification.dto.NotificationDto;
import module.notification.dto.NotificationRequestDto;
import module.notification.services.servicesImpl.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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

public class NotificationController {

    private final NotificationService notificationService;

    @PostMapping("/send")
    @Operation(summary = "Envoyer une notification", description = "Envoie une notification à un destinataire via les canaux spécifiés")
    public CompletableFuture<ResponseEntity<NotificationDto>> sendNotification(
            @Valid @RequestBody NotificationRequestDto request) {

        return notificationService.sendNotification(request)
                .thenApply(ResponseEntity::ok);
    }

    @PostMapping("/send-bulk")
    @Operation(summary = "Envoyer des notifications en masse", description = "Envoie des notifications à plusieurs destinataires")
    public CompletableFuture<ResponseEntity<List<NotificationDto>>> sendBulkNotifications(
            @Valid @RequestBody BulkNotificationDto request) {

        return notificationService.sendBulkNotifications(request)
                .thenApply(ResponseEntity::ok);
    }

    @GetMapping("/recipient/{recipientId}")
    @Operation(summary = "Récupérer les notifications d'un destinataire", description = "Récupère toutes les notifications d'un destinataire avec pagination")
    public ResponseEntity<Page<NotificationDto>> getNotificationsByRecipient(
            @PathVariable @Parameter(description = "ID du destinataire") String recipientId,
            @RequestParam(defaultValue = "0") @Parameter(description = "Numéro de page") int page,
            @RequestParam(defaultValue = "20") @Parameter(description = "Taille de page") int size) {

        Pageable pageable = PageRequest.of(page, size);
        Page<NotificationDto> notifications = notificationService.getNotificationsByRecipient(recipientId, pageable);

        return ResponseEntity.ok(notifications);
    }

    @PutMapping("/{notificationId}/read")
    @Operation(summary = "Marquer une notification comme lue", description = "Marque une notification spécifique comme lue")
    public ResponseEntity<Void> markAsRead(
            @PathVariable @Parameter(description = "ID de la notification") Long notificationId,
            @RequestParam @Parameter(description = "ID du destinataire") String recipientId) {

        notificationService.markAsRead(notificationId, recipientId);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/recipient/{recipientId}/mark-all-read")
    @Operation(summary = "Marquer toutes les notifications comme lues", description = "Marque toutes les notifications d'un destinataire comme lues")
    public ResponseEntity<Void> markAllAsRead(
            @PathVariable @Parameter(description = "ID du destinataire") String recipientId) {

        notificationService.markAllAsRead(recipientId);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/recipient/{recipientId}/unread-count")
    @Operation(summary = "Obtenir le nombre de notifications non lues", description = "Retourne le nombre total de notifications non lues pour un destinataire")
    public ResponseEntity<Long> getUnreadCount(
            @PathVariable @Parameter(description = "ID du destinataire") String recipientId) {

        Long count = notificationService.getUnreadCount(recipientId);
        return ResponseEntity.ok(count);
    }

    @GetMapping("/recipient/{recipientId}/unread")
    @Operation(summary = "Récupérer les notifications non lues", description = "Récupère toutes les notifications non lues d'un destinataire")
    public ResponseEntity<List<NotificationDto>> getUnreadNotifications(
            @PathVariable @Parameter(description = "ID du destinataire") String recipientId) {

        List<NotificationDto> notifications = notificationService.getUnreadNotifications(recipientId);
        return ResponseEntity.ok(notifications);
    }

    @DeleteMapping("/{notificationId}")
    @Operation(summary = "Supprimer une notification", description = "Supprime une notification spécifique")
    public ResponseEntity<Void> deleteNotification(
            @PathVariable @Parameter(description = "ID de la notification") Long notificationId,
            @RequestParam @Parameter(description = "ID du destinataire") String recipientId) {

        notificationService.deleteNotification(notificationId, recipientId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/health")
    @Operation(summary = "Vérifier l'état du service", description = "Endpoint de santé pour vérifier que le service de notification fonctionne")
    public ResponseEntity<String> healthCheck() {
        return ResponseEntity.ok("Notification service is running");
    }
}