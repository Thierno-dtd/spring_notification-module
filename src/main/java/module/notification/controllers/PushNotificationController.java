package module.notification.controllers;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import module.notification.dto.PushNotificationDto;
import module.notification.services.servicesImpl.PushNotificationService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/push-notifications")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "notification.push.enabled", havingValue = "true")
@Tag(name = "Push Notifications", description = "API pour gérer les notifications push")
@Slf4j
public class PushNotificationController {

    private final PushNotificationService pushNotificationService;

    @PostMapping("/send")
    @Operation(
            summary = "Envoyer une notification push directe",
            description = "Envoie une notification push directement à un token spécifique"
    )
    public ResponseEntity<Map<String, Object>> sendDirectPushNotification(
            @Valid @RequestBody PushNotificationDto request) {

        try {
            // Créer une notification temporaire pour le test
            module.notification.entities.Notification notification = createNotificationFromDto(request);

            pushNotificationService.send(notification);

            Map<String, Object> response = Map.of(
                    "success", true,
                    "message", "Notification push envoyée avec succès",
                    "recipientId", request.getRecipientId()
            );

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Erreur lors de l'envoi de notification push: {}", e.getMessage());

            Map<String, Object> response = Map.of(
                    "success", false,
                    "message", "Erreur lors de l'envoi de la notification push",
                    "error", e.getMessage()
            );

            return ResponseEntity.badRequest().body(response);
        }
    }

    @PostMapping("/send-to-topic")
    @Operation(
            summary = "Envoyer une notification push à un topic",
            description = "Envoie une notification push à un topic/groupe"
    )
    public ResponseEntity<Map<String, Object>> sendPushNotificationToTopic(
            @RequestParam @Parameter(description = "Nom du topic") String topic,
            @Valid @RequestBody PushNotificationDto request) {

        try {
            module.notification.entities.Notification notification = createNotificationFromDto(request);

            pushNotificationService.sendToTopic(topic, notification);

            Map<String, Object> response = Map.of(
                    "success", true,
                    "message", "Notification push envoyée au topic avec succès",
                    "topic", topic
            );

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Erreur lors de l'envoi de notification push au topic {}: {}", topic, e.getMessage());

            Map<String, Object> response = Map.of(
                    "success", false,
                    "message", "Erreur lors de l'envoi de la notification push au topic",
                    "topic", topic,
                    "error", e.getMessage()
            );

            return ResponseEntity.badRequest().body(response);
        }
    }

    @PostMapping("/test/{userId}")
    @Operation(
            summary = "Tester une notification push",
            description = "Envoie une notification push de test"
    )
    public ResponseEntity<Map<String, Object>> testPushNotification(
            @PathVariable @Parameter(description = "ID de l'utilisateur de test") String userId,
            @RequestParam(required = false) @Parameter(description = "Token push de test") String pushToken,
            @RequestBody(required = false) @Parameter(description = "Contenu personnalisé pour le test") Map<String, String> customContent) {

        try {
            // Créer une notification de test
            module.notification.entities.Notification testNotification = createTestNotification(userId, pushToken, customContent);

            pushNotificationService.send(testNotification);

            Map<String, Object> response = Map.of(
                    "success", true,
                    "message", "Notification push de test envoyée avec succès",
                    "userId", userId,
                    "testNotificationId", testNotification.getId() != null ? testNotification.getId() : "test-" + System.currentTimeMillis()
            );

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Erreur lors du test de notification push pour l'utilisateur {}: {}", userId, e.getMessage());

            Map<String, Object> response = Map.of(
                    "success", false,
                    "message", "Erreur lors de l'envoi de la notification push de test",
                    "userId", userId,
                    "error", e.getMessage()
            );

            return ResponseEntity.badRequest().body(response);
        }
    }

    @GetMapping("/health")
    @Operation(
            summary = "Vérifier l'état du service push",
            description = "Endpoint de santé pour vérifier le service de notifications push"
    )
    public ResponseEntity<Map<String, Object>> healthCheck() {
        boolean isHealthy = pushNotificationService.healthCheck();
        Map<String, Object> metrics = pushNotificationService.getMetrics();

        Map<String, Object> health = Map.of(
                "status", isHealthy ? "UP" : "DOWN",
                "service", "PushNotificationService",
                "enabled", pushNotificationService.isEnabled(),
                "configured", pushNotificationService.isConfigured(),
                "metrics", metrics
        );

        return ResponseEntity.ok(health);
    }

    @GetMapping("/metrics")
    @Operation(
            summary = "Obtenir les métriques du service push",
            description = "Retourne les métriques détaillées du service de notifications push"
    )
    public ResponseEntity<Map<String, Object>> getMetrics() {
        Map<String, Object> metrics = pushNotificationService.getMetrics();
        return ResponseEntity.ok(metrics);
    }

    private module.notification.entities.Notification createNotificationFromDto(PushNotificationDto dto) {
        return module.notification.entities.Notification.builder()
                .id(System.currentTimeMillis()) // ID temporaire
                .title(dto.getTitle())
                .content(dto.getBody())
                .type(module.notification.enums.NotificationTypeCodes.CUSTOM)
                .priority(module.notification.enums.NotificationPriority.MEDIUM)
                .status(module.notification.enums.NotificationStatus.PENDING)
                .recipientId(dto.getRecipientId())
                .channels(java.util.Set.of(module.notification.enums.ChannelType.PUSH))
                .createdAt(java.time.LocalDateTime.now())
                .parameters(dto.getAdditionalData())
                .build();
    }

    private module.notification.entities.Notification createTestNotification(String userId, String pushToken, Map<String, String> customContent) {
        String title = customContent != null ? customContent.getOrDefault("title", "Test Push Notification") : "Test Push Notification";
        String content = customContent != null ? customContent.getOrDefault("content", "Ceci est une notification push de test.") : "Ceci est une notification push de test.";

        java.util.Map<String, String> parameters = new java.util.HashMap<>(customContent != null ? customContent : java.util.Map.of());
        if (pushToken != null) {
            parameters.put("pushToken", pushToken);
        }

        return module.notification.entities.Notification.builder()
                .id(System.currentTimeMillis()) // ID temporaire pour le test
                .title(title)
                .content(content)
                .type(module.notification.enums.NotificationTypeCodes.SYSTEM)
                .priority(module.notification.enums.NotificationPriority.MEDIUM)
                .status(module.notification.enums.NotificationStatus.PENDING)
                .recipientId(userId)
                .channels(java.util.Set.of(module.notification.enums.ChannelType.PUSH))
                .createdAt(java.time.LocalDateTime.now())
                .parameters(parameters)
                .build();
    }
}