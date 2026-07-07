package module.notification.services.servicesImpl;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import module.notification.entities.NotificationMetric;
import module.notification.enums.ChannelType;
import module.notification.enums.NotificationPriority;
import module.notification.repositories.NotificationMetricRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationMetricsService {

    private final NotificationMetricRepository metricRepository;

    // Cache en mémoire pour les métriques temps réel
    private final Map<String, AtomicLong> realtimeCounters = new ConcurrentHashMap<>();
    private final Map<String, Long> processingTimes = new ConcurrentHashMap<>();

    @Async("notificationTaskExecutor")
    public void recordNotificationSent(Long notificationId, ChannelType channel, String type,
                                       NotificationPriority priority, String recipientId, String templateId,
                                       long processingTimeMs, HttpServletRequest request) {

        recordMetric(notificationId, channel, type, priority, "SENT", recipientId, templateId,
                processingTimeMs, null, 0, request);

        // Incrémenter les compteurs temps réel
        incrementRealtimeCounter("sent_" + channel.name().toLowerCase());
        incrementRealtimeCounter("sent_total");
    }

    @Async("notificationTaskExecutor")
    public void recordNotificationFailed(Long notificationId, ChannelType channel, String type,
                                         NotificationPriority priority, String recipientId, String templateId,
                                         long processingTimeMs, String errorMessage, int retryCount,
                                         HttpServletRequest request) {

        recordMetric(notificationId, channel, type, priority, "FAILED", recipientId, templateId,
                processingTimeMs, errorMessage, retryCount, request);

        // Incrémenter les compteurs temps réel
        incrementRealtimeCounter("failed_" + channel.name().toLowerCase());
        incrementRealtimeCounter("failed_total");
    }

    @Async("notificationTaskExecutor")
    public void recordNotificationRead(Long notificationId, ChannelType channel, String type,
                                       String recipientId, HttpServletRequest request) {

        recordMetric(notificationId, channel, type, null, "READ", recipientId, null,
                null, null, 0, request);

        // Incrémenter les compteurs temps réel
        incrementRealtimeCounter("read_" + (channel != null ? channel.name().toLowerCase() : "unknown"));
        incrementRealtimeCounter("read_total");
    }

    @Async("notificationTaskExecutor")
    public void recordNotificationDelivered(Long notificationId, ChannelType channel, String type,
                                            String recipientId, long deliveryTimeMs) {

        recordMetric(notificationId, channel, type, null, "DELIVERED", recipientId, null,
                deliveryTimeMs, null, 0, null);

        // Incrémenter les compteurs temps réel
        incrementRealtimeCounter("delivered_" + channel.name().toLowerCase());
        incrementRealtimeCounter("delivered_total");
    }

    private void recordMetric(Long notificationId, ChannelType channel, String type,
                              NotificationPriority priority, String status, String recipientId,
                              String templateId, Long processingTimeMs, String errorMessage,
                              int retryCount, HttpServletRequest request) {
        try {
            NotificationMetric.NotificationMetricBuilder metricBuilder = NotificationMetric.builder()
                    .notificationId(notificationId)
                    .channel(channel)
                    .type(type)
                    .priority(priority)
                    .status(status)
                    .recipientId(recipientId)
                    .templateId(templateId)
                    .processingTimeMs(processingTimeMs)
                    .errorMessage(errorMessage)
                    .retryCount(retryCount)
                    .timestamp(LocalDateTime.now());

            // Extraire les informations de la requête si disponible
            if (request != null) {
                metricBuilder
                        .userAgent(request.getHeader("User-Agent"))
                        .ipAddress(getClientIpAddress(request))
                        .deviceType(extractDeviceType(request.getHeader("User-Agent")));
            }

            NotificationMetric metric = metricBuilder.build();
            metricRepository.save(metric);

        } catch (Exception e) {
            log.error("Erreur lors de l'enregistrement de la métrique: {}", e.getMessage(), e);
        }
    }

    /**
     * Statistiques en temps réel
     */
    public Map<String, Object> getRealtimeMetrics() {
        Map<String, Object> metrics = new HashMap<>();

        // Compteurs temps réel
        realtimeCounters.forEach((key, value) -> metrics.put(key, value.get()));

        // Ajouter des métriques calculées
        long totalSent = realtimeCounters.getOrDefault("sent_total", new AtomicLong(0)).get();
        long totalFailed = realtimeCounters.getOrDefault("failed_total", new AtomicLong(0)).get();
        long total = totalSent + totalFailed;

        if (total > 0) {
            metrics.put("success_rate", (double) totalSent / total * 100);
            metrics.put("failure_rate", (double) totalFailed / total * 100);
        } else {
            metrics.put("success_rate", 0.0);
            metrics.put("failure_rate", 0.0);
        }

        metrics.put("total_processed", total);
        metrics.put("last_updated", LocalDateTime.now());

        return metrics;
    }

    /**
     * Statistiques par période
     */
    public Map<String, Object> getPeriodStatistics(LocalDateTime startDate, LocalDateTime endDate) {
        Map<String, Object> stats = new HashMap<>();

        // Statistiques par canal
        List<Object[]> channelStats = metricRepository.getChannelStatistics(startDate, endDate);
        Map<String, Map<String, Long>> channelMetrics = new HashMap<>();

        for (Object[] row : channelStats) {
            String channel = ((ChannelType) row[0]).name();
            String status = (String) row[1];
            Long count = (Long) row[2];

            channelMetrics.computeIfAbsent(channel, k -> new HashMap<>()).put(status, count);
        }
        stats.put("channels", channelMetrics);

        // Statistiques par type
        List<Object[]> typeStats = metricRepository.getTypeStatistics(startDate, endDate);
        Map<String, Map<String, Long>> typeMetrics = new HashMap<>();

        for (Object[] row : typeStats) {
            String type = row[0] != null ? row[0].toString() : "UNKNOWN";
            String status = (String) row[1];
            Long count = (Long) row[2];

            typeMetrics.computeIfAbsent(type, k -> new HashMap<>()).put(status, count);
        }
        stats.put("types", typeMetrics);

        // Temps de traitement moyen
        List<Object[]> processingTimes = metricRepository.getAverageProcessingTimeByChannel(startDate, endDate);
        Map<String, Double> avgProcessingTimes = new HashMap<>();

        for (Object[] row : processingTimes) {
            String channel = ((ChannelType) row[0]).name();
            Double avgTime = (Double) row[1];
            avgProcessingTimes.put(channel, avgTime);
        }
        stats.put("average_processing_times", avgProcessingTimes);

        // Top erreurs
        List<Object[]> topErrors = metricRepository.getTopErrors(startDate, endDate);
        List<Map<String, Object>> errorStats = topErrors.stream()
                .limit(10)
                .map(row -> Map.<String, Object>of(
                        "error", row[0] != null ? row[0] : "Unknown error",
                        "count", row[1]
                ))
                .collect(Collectors.toList());
        stats.put("top_errors", errorStats);

        return stats;
    }

    /**
     * Dashboard metrics
     */
    public Map<String, Object> getDashboardMetrics() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime last24h = now.minus(24, ChronoUnit.HOURS);
        LocalDateTime last7d = now.minus(7, ChronoUnit.DAYS);

        Map<String, Object> dashboard = new HashMap<>();

        // Métriques 24h
        Map<String, Object> last24hStats = getPeriodStatistics(last24h, now);
        dashboard.put("last_24h", last24hStats);

        // Métriques 7 jours
        Map<String, Object> last7dStats = getPeriodStatistics(last7d, now);
        dashboard.put("last_7d", last7dStats);

        // Statistiques horaires pour les graphiques
        List<Object[]> hourlyStats = metricRepository.getHourlyStatistics(last24h, now);
        Map<Integer, Map<String, Long>> hourlyMetrics = new HashMap<>();

        for (Object[] row : hourlyStats) {
            Integer hour = (Integer) row[0];
            String status = (String) row[1];
            Long count = (Long) row[2];

            hourlyMetrics.computeIfAbsent(hour, k -> new HashMap<>()).put(status, count);
        }
        dashboard.put("hourly_stats", hourlyMetrics);

        // Taux de succès par canal
        List<Object[]> successRates = metricRepository.getSuccessRateByChannel(last24h, now);
        Map<String, Map<String, Object>> channelSuccessRates = new HashMap<>();

        for (Object[] row : successRates) {
            String channel = ((ChannelType) row[0]).name();
            Long sent = (Long) row[1];
            Long failed = (Long) row[2];
            Long total = (Long) row[3];

            double successRate = total > 0 ? (double) sent / total * 100 : 0;

            channelSuccessRates.put(channel, Map.of(
                    "sent", sent,
                    "failed", failed,
                    "total", total,
                    "success_rate", successRate
            ));
        }
        dashboard.put("channel_success_rates", channelSuccessRates);

        // Top utilisateurs
        List<Object[]> topRecipients = metricRepository.getTopRecipients(last7d, now, PageRequest.of(0, 10));
        List<Map<String, Object>> topUsers = topRecipients.stream()
                .map(row -> Map.<String, Object>of(
                        "recipient_id", row[0],
                        "notification_count", row[1]
                ))
                .collect(Collectors.toList());
        dashboard.put("top_recipients", topUsers);

        return dashboard;
    }

    /**
     * Métriques pour un template spécifique
     */
    public Map<String, Object> getTemplateMetrics(String templateId, LocalDateTime startDate, LocalDateTime endDate) {
        List<Object[]> templateStats = metricRepository.getTemplateMetrics(templateId, startDate, endDate);

        Map<String, Long> statusCounts = new HashMap<>();
        for (Object[] row : templateStats) {
            String status = (String) row[0];
            Long count = (Long) row[1];
            statusCounts.put(status, count);
        }

        long total = statusCounts.values().stream().mapToLong(Long::longValue).sum();
        double successRate = total > 0 ? (double) statusCounts.getOrDefault("SENT", 0L) / total * 100 : 0;

        return Map.of(
                "template_id", templateId,
                "period", Map.of("start", startDate, "end", endDate),
                "status_breakdown", statusCounts,
                "total_notifications", total,
                "success_rate", successRate
        );
    }

    private void incrementRealtimeCounter(String key) {
        realtimeCounters.computeIfAbsent(key, k -> new AtomicLong(0)).incrementAndGet();
    }

    /**
     * Réinitialise les compteurs temps réel
     */
    public void resetRealtimeCounters() {
        realtimeCounters.clear();
        log.info("Compteurs temps réel réinitialisés");
    }

    /**
     * Nettoyage périodique des anciennes métriques
     */
    @Scheduled(cron = "0 0 2 * * ?") // 2h du matin tous les jours
    @Transactional
    public void cleanupOldMetrics() {
        LocalDateTime cutoffDate = LocalDateTime.now().minusMonths(6);
        int deletedCount = metricRepository.deleteByDateCreatedBefore(cutoffDate);

        if (deletedCount > 0) {
            log.info("Nettoyage des métriques: {} entrées supprimées", deletedCount);
        }
    }

    /**
     * Utilitaires pour extraire les informations de la requête
     */
    private String getClientIpAddress(HttpServletRequest request) {
        String xForwardedForHeader = request.getHeader("X-Forwarded-For");
        if (xForwardedForHeader == null) {
            return request.getRemoteAddr();
        } else {
            return xForwardedForHeader.split(",")[0];
        }
    }

    private String extractDeviceType(String userAgent) {
        if (userAgent == null) return "UNKNOWN";

        userAgent = userAgent.toLowerCase();
        if (userAgent.contains("mobile") || userAgent.contains("android") || userAgent.contains("iphone")) {
            return "MOBILE";
        } else if (userAgent.contains("tablet") || userAgent.contains("ipad")) {
            return "TABLET";
        } else if (userAgent.contains("mozilla") || userAgent.contains("chrome") || userAgent.contains("safari")) {
            return "DESKTOP";
        } else {
            return "OTHER";
        }
    }
}