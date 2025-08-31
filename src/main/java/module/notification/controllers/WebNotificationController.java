package module.notification.controllers;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import module.notification.services.servicesImpl.WebNotificationService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/web-notifications")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
@ConditionalOnProperty(name = "notification.web.enabled", havingValue = "true", matchIfMissing = true)
@Tag(name = "Web Notifications", description = "API pour gérer les notifications web en temps réel")
@Slf4j
public class WebNotificationController {

    private final WebNotificationService webNotificationService;

    @GetMapping("/user/{userId}")
    @Operation(
            summary = "Récupérer les notifications web d'un utilisateur",
            description = "Récupère toutes les notifications web d'un utilisateur depuis le cache local"
    )
    public ResponseEntity<Map<String, Object>> getUserNotifications(
            @PathVariable @Parameter(description = "ID de l'utilisateur") String userId,
            @RequestParam(defaultValue = "50") @Parameter(description = "Nombre maximum de notifications à retourner") int limit) {

        Map<String, Object> notifications = webNotificationService.getWebNotificationsForUser(userId, limit);
        return ResponseEntity.ok(notifications);
    }

    @GetMapping("/user/{userId}/recent")
    @Operation(
            summary = "Récupérer les notifications web récentes",
            description = "Récupère les notifications web récentes d'un utilisateur (par défaut les 10 dernières)"
    )
    public ResponseEntity<Map<String, Object>> getRecentUserNotifications(
            @PathVariable @Parameter(description = "ID de l'utilisateur") String userId) {

        Map<String, Object> notifications = webNotificationService.getWebNotificationsForUser(userId, 10);
        return ResponseEntity.ok(notifications);
    }

    @PutMapping("/user/{userId}/notification/{notificationId}/read")
    @Operation(
            summary = "Marquer une notification web comme lue",
            description = "Marque une notification web spécifique comme lue dans le cache local"
    )
    public ResponseEntity<Map<String, Object>> markWebNotificationAsRead(
            @PathVariable @Parameter(description = "ID de l'utilisateur") String userId,
            @PathVariable @Parameter(description = "ID de la notification") Long notificationId) {

        boolean success = webNotificationService.markWebNotificationAsRead(userId, notificationId);

        Map<String, Object> response = Map.of(
                "success", success,
                "message", success ? "Notification marquée comme lue" : "Notification non trouvée"
        );

        return ResponseEntity.ok(response);
    }

    @PutMapping("/user/{userId}/cleanup")
    @Operation(
            summary = "Nettoyer les notifications web anciennes",
            description = "Supprime les notifications web anciennes du cache pour un utilisateur"
    )
    public ResponseEntity<Map<String, Object>> cleanupUserNotifications(
            @PathVariable @Parameter(description = "ID de l'utilisateur") String userId) {

        webNotificationService.cleanupWebNotificationsForUser(userId);

        Map<String, Object> response = Map.of(
                "success", true,
                "message", "Nettoyage effectué avec succès"
        );

        return ResponseEntity.ok(response);
    }

    @GetMapping("/health")
    @Operation(
            summary = "Vérifier l'état du service web",
            description = "Endpoint de santé pour vérifier le service de notifications web"
    )
    public ResponseEntity<Map<String, Object>> healthCheck() {
        boolean isHealthy = webNotificationService.healthCheck();
        Map<String, Object> metrics = webNotificationService.getMetrics();

        Map<String, Object> health = Map.of(
                "status", isHealthy ? "UP" : "DOWN",
                "service", "WebNotificationService",
                "metrics", metrics
        );

        return ResponseEntity.ok(health);
    }

    @GetMapping("/metrics")
    @Operation(
            summary = "Obtenir les métriques du service web",
            description = "Retourne les métriques détaillées du service de notifications web"
    )
    public ResponseEntity<Map<String, Object>> getMetrics() {
        Map<String, Object> metrics = webNotificationService.getMetrics();
        return ResponseEntity.ok(metrics);
    }

    @PostMapping("/test/{userId}")
    @Operation(
            summary = "Tester une notification web",
            description = "Envoie une notification de test via le service web pour vérifier le fonctionnement"
    )
    public ResponseEntity<Map<String, Object>> testWebNotification(
            @PathVariable @Parameter(description = "ID de l'utilisateur de test") String userId,
            @RequestBody(required = false) @Parameter(description = "Contenu personnalisé pour le test") Map<String, String> customContent) {

        try {
            // Créer une notification de test
            module.notification.entities.Notification testNotification = createTestNotification(userId, customContent);

            // L'envoyer via le service web
            webNotificationService.send(testNotification);

            Map<String, Object> response = Map.of(
                    "success", true,
                    "message", "Notification de test envoyée avec succès",
                    "userId", userId,
                    "testNotificationId", testNotification.getId() != null ? testNotification.getId() : "test-" + System.currentTimeMillis()
            );

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Erreur lors du test de notification web pour l'utilisateur {}: {}", userId, e.getMessage());

            Map<String, Object> response = Map.of(
                    "success", false,
                    "message", "Erreur lors de l'envoi de la notification de test",
                    "error", e.getMessage()
            );

            return ResponseEntity.badRequest().body(response);
        }
    }

    private module.notification.entities.Notification createTestNotification(String userId, Map<String, String> customContent) {
        String title = customContent != null ? customContent.getOrDefault("title", "Notification de Test") : "Notification de Test";
        String content = customContent != null ? customContent.getOrDefault("content", "Ceci est une notification de test pour vérifier le fonctionnement du service web.") : "Ceci est une notification de test pour vérifier le fonctionnement du service web.";

        return module.notification.entities.Notification.builder()
                .id(System.currentTimeMillis()) // ID temporaire pour le test
                .title(title)
                .content(content)
                .type(module.notification.enums.NotificationType.SYSTEM)
                .priority(module.notification.enums.NotificationPriority.MEDIUM)
                .status(module.notification.enums.NotificationStatus.PENDING)
                .recipientId(userId)
                .channels(java.util.Set.of(module.notification.enums.ChannelType.WEB))
                .createdAt(java.time.LocalDateTime.now())
                .parameters(customContent)
                .build();
    }
}