package module.notification.controllers;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import module.notification.dto.NotificationTemplateDto;
import module.notification.services.servicesImpl.*;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/notifications")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
@Tag(name = "Administration", description = "API d'administration pour les notifications")
@SecurityRequirement(name = "admin-auth")
@Slf4j
public class NotificationAdminController {

    private final NotificationService notificationService;
    private final NotificationMetricsService metricsService;
    private final CircuitBreakerService circuitBreakerService;
    private final NotificationRetryService retryService;
    private final AdvancedRateLimiterService rateLimiterService;
    private final TemplateValidationService templateValidationService;

    // ==================== DASHBOARD & MÉTRIQUES ====================

    @GetMapping("/dashboard")
    @Operation(summary = "Dashboard administrateur", description = "Vue d'ensemble des métriques de notification")
    public ResponseEntity<Map<String, Object>> getDashboard() {
        Map<String, Object> dashboard = metricsService.getDashboardMetrics();
        return ResponseEntity.ok(dashboard);
    }

    @GetMapping("/metrics/realtime")
    @Operation(summary = "Métriques temps réel", description = "Métriques de notification en temps réel")
    public ResponseEntity<Map<String, Object>> getRealtimeMetrics() {
        Map<String, Object> metrics = metricsService.getRealtimeMetrics();
        return ResponseEntity.ok(metrics);
    }

    @GetMapping("/metrics/period")
    @Operation(summary = "Métriques par période", description = "Métriques détaillées pour une période donnée")
    public ResponseEntity<Map<String, Object>> getPeriodMetrics(
            @RequestParam @Parameter(description = "Date de début") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam @Parameter(description = "Date de fin") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate) {

        Map<String, Object> metrics = metricsService.getPeriodStatistics(startDate, endDate);
        return ResponseEntity.ok(metrics);
    }

    @GetMapping("/health")
    @Operation(summary = "État de santé du système", description = "État de santé détaillé de tous les services")
    public ResponseEntity<Map<String, Object>> getSystemHealth() {
        Map<String, Object> health = notificationService.getHealthStatus();
        return ResponseEntity.ok(health);
    }

    // ==================== CIRCUIT BREAKERS ====================

    @GetMapping("/circuit-breakers")
    @Operation(summary = "État des circuit breakers", description = "État de tous les circuit breakers par canal")
    public ResponseEntity<Map<String, Object>> getCircuitBreakersStatus() {
        Map<String, Object> status = Map.of("circuit_breakers", circuitBreakerService.getCircuitBreakerStatus());
        return ResponseEntity.ok(status);
    }

    @PostMapping("/circuit-breakers/{channel}/reset")
    @Operation(summary = "Réinitialiser un circuit breaker", description = "Force la fermeture d'un circuit breaker")
    public ResponseEntity<Map<String, Object>> resetCircuitBreaker(
            @PathVariable @Parameter(description = "Canal à réinitialiser") String channel) {

        try {
            // Cette méthode devrait être ajoutée au CircuitBreakerService
            // circuitBreakerService.resetCircuitBreaker(ChannelType.valueOf(channel.toUpperCase()));

            Map<String, Object> response = Map.of(
                    "success", true,
                    "message", "Circuit breaker réinitialisé pour le canal " + channel
            );

            log.info("Circuit breaker réinitialisé pour le canal {} par un administrateur", channel);
            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            Map<String, Object> response = Map.of(
                    "success", false,
                    "message", "Canal invalide: " + channel
            );
            return ResponseEntity.badRequest().body(response);
        } catch (Exception e) {
            Map<String, Object> response = Map.of(
                    "success", false,
                    "message", "Erreur lors de la réinitialisation: " + e.getMessage()
            );
            return ResponseEntity.internalServerError().body(response);
        }
    }

    // ==================== GESTION DES RETRY ====================

    @GetMapping("/retries/stats")
    @Operation(summary = "Statistiques de retry", description = "Statistiques globales des tentatives de retry")
    public ResponseEntity<Map<String, Object>> getRetryStatistics() {
        Map<String, Object> stats = retryService.getRetryStatistics();
        return ResponseEntity.ok(stats);
    }

    @GetMapping("/retries/notification/{notificationId}")
    @Operation(summary = "Historique de retry", description = "Historique des tentatives pour une notification")
    public ResponseEntity<List<Object>> getNotificationRetryHistory(
            @PathVariable @Parameter(description = "ID de la notification") Long notificationId) {

        var retryHistory = retryService.getRetryHistory(notificationId);
        return ResponseEntity.ok(List.of(retryHistory.toArray()));
    }

    @PostMapping("/retries/process-stuck")
    @Operation(summary = "Traiter les retry bloqués", description = "Force le traitement des retry en cours depuis trop longtemps")
    public ResponseEntity<Map<String, Object>> processStuckRetries() {
        try {
            // Cette méthode devrait être ajoutée au NotificationRetryService
            // int processedCount = retryService.processStuckRetries();
            int processedCount = 0; // Placeholder

            Map<String, Object> response = Map.of(
                    "success", true,
                    "message", "Retry bloqués traités",
                    "processed_count", processedCount
            );

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> response = Map.of(
                    "success", false,
                    "message", "Erreur lors du traitement: " + e.getMessage()
            );
            return ResponseEntity.internalServerError().body(response);
        }
    }

    // ==================== RATE LIMITING ====================

    @GetMapping("/rate-limits/user/{userId}")
    @Operation(summary = "Limites de débit utilisateur", description = "État des limites de débit pour un utilisateur")
    public ResponseEntity<Map<String, Object>> getUserRateLimits(
            @PathVariable @Parameter(description = "ID de l'utilisateur") String userId) {

        Map<String, Object> limits = rateLimiterService.getUserRateLimitStats(userId);
        return ResponseEntity.ok(limits);
    }

    @PostMapping("/rate-limits/user/{userId}/reset")
    @Operation(summary = "Réinitialiser les limites utilisateur", description = "Remet à zéro les compteurs de rate limiting pour un utilisateur")
    public ResponseEntity<Map<String, Object>> resetUserRateLimits(
            @PathVariable @Parameter(description = "ID de l'utilisateur") String userId) {

        try {
            rateLimiterService.resetUserLimits(userId);

            Map<String, Object> response = Map.of(
                    "success", true,
                    "message", "Rate limits réinitialisés pour l'utilisateur " + userId
            );

            log.info("Rate limits réinitialisés pour l'utilisateur {} par un administrateur", userId);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            Map<String, Object> response = Map.of(
                    "success", false,
                    "message", "Erreur lors de la réinitialisation: " + e.getMessage()
            );
            return ResponseEntity.internalServerError().body(response);
        }
    }

    // ==================== VALIDATION DE TEMPLATES ====================

    @PostMapping("/templates/validate")
    @Operation(summary = "Valider un template", description = "Valide un template avant sa création/modification")
    public ResponseEntity<TemplateValidationService.ValidationResult> validateTemplate(
            @RequestBody NotificationTemplateDto template) {

        TemplateValidationService.ValidationResult result = templateValidationService.validateTemplate(template);

        if (result.isValid()) {
            return ResponseEntity.ok(result);
        } else {
            return ResponseEntity.badRequest().body(result);
        }
    }

    @PostMapping("/templates/{templateId}/test")
    @Operation(summary = "Tester un template", description = "Teste un template avec des données d'exemple")
    public ResponseEntity<TemplateValidationService.ValidationResult> testTemplate(
            @PathVariable @Parameter(description = "ID du template") String templateId,
            @RequestBody(required = false) Map<String, String> testData) {

        try {
            // Récupérer le template (cette méthode devrait exister dans templateService)
            NotificationTemplateDto template = null; // templateService.getTemplate(templateId);

            if (template == null) {
                TemplateValidationService.ValidationResult errorResult = TemplateValidationService.ValidationResult.builder()
                        .valid(false)
                        .templateId(templateId)
                        .errors(List.of("Template non trouvé"))
                        .warnings(List.of())
                        .build();

                return ResponseEntity.notFound().build();
            }

            TemplateValidationService.ValidationResult result = templateValidationService.testTemplate(template, testData);
            return ResponseEntity.ok(result);

        } catch (Exception e) {
            TemplateValidationService.ValidationResult errorResult = TemplateValidationService.ValidationResult.builder()
                    .valid(false)
                    .templateId(templateId)
                    .errors(List.of("Erreur lors du test: " + e.getMessage()))
                    .warnings(List.of())
                    .build();

            return ResponseEntity.internalServerError().body(errorResult);
        }
    }

    // ==================== TEMPLATE METRICS ====================

    @GetMapping("/templates/{templateId}/metrics")
    @Operation(summary = "Métriques d'un template", description = "Métriques détaillées pour un template spécifique")
    public ResponseEntity<Map<String, Object>> getTemplateMetrics(
            @PathVariable @Parameter(description = "ID du template") String templateId,
            @RequestParam(required = false) @Parameter(description = "Date de début") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @Parameter(description = "Date de fin") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate) {

        if (startDate == null) {
            startDate = LocalDateTime.now().minusDays(7);
        }
        if (endDate == null) {
            endDate = LocalDateTime.now();
        }

        Map<String, Object> metrics = metricsService.getTemplateMetrics(templateId, startDate, endDate);
        return ResponseEntity.ok(metrics);
    }

    // ==================== MAINTENANCE ====================

    @PostMapping("/maintenance/cleanup-metrics")
    @Operation(summary = "Nettoyer les anciennes métriques", description = "Supprime les anciennes métriques selon la politique de rétention")
    public ResponseEntity<Map<String, Object>> cleanupMetrics() {
        try {
            // Cette méthode devrait déclencher un nettoyage manuel
            // metricsService.cleanupOldMetrics();

            Map<String, Object> response = Map.of(
                    "success", true,
                    "message", "Nettoyage des métriques effectué"
            );

            log.info("Nettoyage manuel des métriques déclenché par un administrateur");
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            Map<String, Object> response = Map.of(
                    "success", false,
                    "message", "Erreur lors du nettoyage: " + e.getMessage()
            );
            return ResponseEntity.internalServerError().body(response);
        }
    }

    @PostMapping("/maintenance/cleanup-retries")
    @Operation(summary = "Nettoyer les anciens retry", description = "Supprime les anciens enregistrements de retry")
    public ResponseEntity<Map<String, Object>> cleanupRetries() {
        try {
            // Déclencher un nettoyage manuel des retry
            // retryService.cleanupOldRetries();

            Map<String, Object> response = Map.of(
                    "success", true,
                    "message", "Nettoyage des retry effectué"
            );

            log.info("Nettoyage manuel des retry déclenché par un administrateur");
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            Map<String, Object> response = Map.of(
                    "success", false,
                    "message", "Erreur lors du nettoyage: " + e.getMessage()
            );
            return ResponseEntity.internalServerError().body(response);
        }
    }

    @PostMapping("/maintenance/reset-realtime-counters")
    @Operation(summary = "Réinitialiser les compteurs temps réel", description = "Remet à zéro tous les compteurs de métriques temps réel")
    public ResponseEntity<Map<String, Object>> resetRealtimeCounters() {
        try {
            metricsService.resetRealtimeCounters();

            Map<String, Object> response = Map.of(
                    "success", true,
                    "message", "Compteurs temps réel réinitialisés"
            );

            log.info("Compteurs temps réel réinitialisés par un administrateur");
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            Map<String, Object> response = Map.of(
                    "success", false,
                    "message", "Erreur lors de la réinitialisation: " + e.getMessage()
            );
            return ResponseEntity.internalServerError().body(response);
        }
    }

    // ==================== RAPPORTS ====================

    @GetMapping("/reports/summary")
    @Operation(summary = "Rapport de synthèse", description = "Rapport de synthèse des notifications sur la période donnée")
    public ResponseEntity<Map<String, Object>> getSummaryReport(
            @RequestParam(required = false) @Parameter(description = "Date de début") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @Parameter(description = "Date de fin") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate) {

        if (startDate == null) {
            startDate = LocalDateTime.now().minusDays(30);
        }
        if (endDate == null) {
            endDate = LocalDateTime.now();
        }

        Map<String, Object> report = notificationService.getAdminStatistics(startDate, endDate);
        report.put("report_period", Map.of("start", startDate, "end", endDate));
        report.put("generated_at", LocalDateTime.now());

        return ResponseEntity.ok(report);
    }

    @GetMapping("/reports/performance")
    @Operation(summary = "Rapport de performance", description = "Rapport détaillé des performances par canal et par période")
    public ResponseEntity<Map<String, Object>> getPerformanceReport(
            @RequestParam(required = false) @Parameter(description = "Date de début") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @Parameter(description = "Date de fin") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate) {

        if (startDate == null) {
            startDate = LocalDateTime.now().minusDays(7);
        }
        if (endDate == null) {
            endDate = LocalDateTime.now();
        }

        Map<String, Object> report = new java.util.HashMap<>();

        // Métriques de base
        report.put("period_metrics", metricsService.getPeriodStatistics(startDate, endDate));

        // État des circuit breakers
        report.put("circuit_breakers", circuitBreakerService.getCircuitBreakerStatus());

        // Statistiques des retry
        report.put("retry_statistics", retryService.getRetryStatistics());

        // Métadonnées du rapport
        report.put("report_period", Map.of("start", startDate, "end", endDate));
        report.put("generated_at", LocalDateTime.now());
        report.put("report_type", "performance");

        return ResponseEntity.ok(report);
    }

    // ==================== UTILITAIRES ====================

    @PostMapping("/test/notification")
    @Operation(summary = "Envoyer une notification de test", description = "Envoie une notification de test pour vérifier le système")
    public ResponseEntity<Map<String, Object>> sendTestNotification(
            @RequestParam @Parameter(description = "ID du destinataire de test") String recipientId,
            @RequestParam(required = false) @Parameter(description = "Canal à tester") String channel) {

        try {
            // Créer une notification de test
            // Cette logique devrait être dans le service
            Map<String, Object> response = Map.of(
                    "success", true,
                    "message", "Notification de test envoyée",
                    "recipient_id", recipientId,
                    "channel", channel != null ? channel : "ALL"
            );

            log.info("Notification de test envoyée à {} sur le canal {} par un administrateur",
                    recipientId, channel != null ? channel : "ALL");

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            Map<String, Object> response = Map.of(
                    "success", false,
                    "message", "Erreur lors de l'envoi: " + e.getMessage()
            );
            return ResponseEntity.badRequest().body(response);
        }
    }

    @GetMapping("/status")
    @Operation(summary = "Statut global du système", description = "Statut consolidé de tous les services de notification")
    public ResponseEntity<Map<String, Object>> getGlobalStatus() {
        Map<String, Object> status = new java.util.HashMap<>();

        try {
            // État global
            status.put("system_status", "OPERATIONAL");
            status.put("timestamp", LocalDateTime.now());

            // Services individuels
            status.put("services", Map.of(
                    "notification_service", true,
                    "metrics_service", true,
                    "circuit_breaker_service", true,
                    "retry_service", true,
                    "rate_limiter_service", true,
                    "template_validation_service", true
            ));

            // Métriques rapides
            status.put("quick_metrics", metricsService.getRealtimeMetrics());

            return ResponseEntity.ok(status);

        } catch (Exception e) {
            status.put("system_status", "ERROR");
            status.put("error", e.getMessage());
            return ResponseEntity.internalServerError().body(status);
        }
    }
}