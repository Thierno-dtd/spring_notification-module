package module.notification.services.servicesImpl;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import module.notification.config.NotificationProperties;
import module.notification.entities.Notification;
import module.notification.entities.NotificationTemplate;
import module.notification.enums.ChannelType;
import module.notification.enums.NotificationPriority;
import module.notification.exceptions.NotificationException;
import module.notification.repositories.NotificationTemplateRepository;
import module.notification.services.Iservices.NotificationChannelService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import jakarta.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "notification.web.enabled", havingValue = "true", matchIfMissing = true)
@Slf4j
public class WebNotificationService implements NotificationChannelService {

    private final NotificationProperties properties;
    private final NotificationTemplateRepository templateRepository;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate = new RestTemplate();

    // Cache local pour les notifications web récentes
    private final Map<String, NotificationWebCache> webNotificationCache = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        log.info("Service de notification Web initialisé avec succès");

        // Démarrer le nettoyage périodique du cache
        startCacheCleanup();
    }

    @Override
    public ChannelType getChannelType() {
        return ChannelType.WEB;
    }

    @Override
    public void send(Notification notification) throws Exception {
        validateNotification(notification);

        try {
            // 1. Stocker la notification dans le cache local pour affichage web
            storeInWebCache(notification);

            // 2. Si configuré, envoyer via webhook ou API externe
            if (hasWebhookConfiguration()) {
                sendViaWebhook(notification);
            }

            // 3. Si configuré avec une API de notifications web navigateur
            if (hasWebPushConfiguration()) {
                sendWebPushNotification(notification);
            }

            log.info("Notification web traitée avec succès pour le destinataire: {}",
                    notification.getRecipientId());

        } catch (Exception e) {
            log.error("Erreur lors du traitement de la notification web pour {}: {}",
                    notification.getRecipientId(), e.getMessage());
            throw new NotificationException("Échec du traitement de la notification web", e);
        }
    }

    @Override
    public boolean isEnabled() {
        // Le canal web est activé par défaut car il ne nécessite pas de configuration externe obligatoire
        return true;
    }

    @Override
    public boolean isConfigured() {
        // Le service web est toujours considéré comme configuré (minimum viable)
        return true;
    }

    @Override
    public void validateNotification(Notification notification) {
        NotificationChannelService.super.validateNotification(notification);

        // Validation spécifique au web
        if (!StringUtils.hasText(notification.getRecipientId())) {
            throw new IllegalArgumentException("ID du destinataire requis pour la notification web");
        }
    }

    @Override
    public boolean supportsPriority(NotificationPriority priority) {
        return true; // Le web supporte toutes les priorités
    }

    @Override
    public Map<String, Integer> getRateLimits() {
        return Map.of(
                "per_minute", 30,  // Limite raisonnable pour les notifications web
                "per_hour", 500
        );
    }

    @Override
    public boolean healthCheck() {
        try {
            // Vérifier que le cache fonctionne
            return webNotificationCache != null && isEnabled();
        } catch (Exception e) {
            log.warn("Health check Web échoué: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public Map<String, Object> getMetrics() {
        Map<String, Object> baseMetrics = NotificationChannelService.super.getMetrics();
        baseMetrics.put("cached_notifications", webNotificationCache.size());
        baseMetrics.put("webhook_configured", hasWebhookConfiguration());
        baseMetrics.put("web_push_configured", hasWebPushConfiguration());
        return baseMetrics;
    }

    // Méthodes publiques spécifiques au service web

    /**
     * Récupère les notifications web pour un utilisateur
     */
    public Map<String, Object> getWebNotificationsForUser(String userId) {
        return getWebNotificationsForUser(userId, 50); // Par défaut, les 50 dernières
    }

    /**
     * Récupère les notifications web pour un utilisateur avec limite
     */
    public Map<String, Object> getWebNotificationsForUser(String userId, int limit) {
        NotificationWebCache userCache = webNotificationCache.get(userId);

        if (userCache == null) {
            return Map.of(
                    "notifications", new java.util.ArrayList<>(),
                    "unreadCount", 0,
                    "lastUpdate", LocalDateTime.now().toString()
            );
        }

        // Limiter le nombre de notifications retournées
        var notifications = userCache.getNotifications().stream()
                .limit(limit)
                .toList();

        return Map.of(
                "notifications", notifications,
                "unreadCount", userCache.getUnreadCount(),
                "lastUpdate", userCache.getLastUpdate().toString()
        );
    }

    /**
     * Marque une notification web comme lue
     */
    public boolean markWebNotificationAsRead(String userId, Long notificationId) {
        NotificationWebCache userCache = webNotificationCache.get(userId);
        if (userCache != null) {
            return userCache.markAsRead(notificationId);
        }
        return false;
    }

    /**
     * Nettoie les notifications web anciennes pour un utilisateur
     */
    public void cleanupWebNotificationsForUser(String userId) {
        NotificationWebCache userCache = webNotificationCache.get(userId);
        if (userCache != null) {
            userCache.cleanup();
        }
    }

    // Méthodes privées

    private void storeInWebCache(Notification notification) {
        String userId = notification.getRecipientId();

        webNotificationCache.computeIfAbsent(userId, k -> new NotificationWebCache())
                .addNotification(convertToWebNotification(notification));

        log.debug("Notification {} ajoutée au cache web pour l'utilisateur {}",
                notification.getId(), userId);
    }

    private Map<String, Object> convertToWebNotification(Notification notification) {
        Map<String, Object> webNotification = new HashMap<>();

        webNotification.put("id", notification.getId());
        webNotification.put("title", notification.getTitle());
        webNotification.put("content", processContent(notification));
        webNotification.put("type", notification.getType().name());
        webNotification.put("priority", notification.getPriority().name());
        webNotification.put("status", notification.getStatus().name());
        webNotification.put("createdAt", notification.getCreatedAt().toString());
        webNotification.put("isRead", notification.getReadAt() != null);

        // Ajouter les paramètres personnalisés
        if (notification.getParameters() != null && !notification.getParameters().isEmpty()) {
            webNotification.put("parameters", notification.getParameters());
        }

        // Ajouter l'ID externe si présent
        if (StringUtils.hasText(notification.getExternalId())) {
            webNotification.put("externalId", notification.getExternalId());
        }

        return webNotification;
    }

    private String processContent(Notification notification) {
        if (StringUtils.hasText(notification.getTemplateId())) {
            try {
                NotificationTemplate template = templateRepository
                        .findByIdAndIsActiveTrue(notification.getTemplateId())
                        .orElse(null);

                if (template != null && StringUtils.hasText(template.getWebTemplate())) {
                    return processTemplate(template.getWebTemplate(), notification.getParameters());
                }
            } catch (Exception e) {
                log.warn("Erreur lors du traitement du contenu Web via template {}: {}",
                        notification.getTemplateId(), e.getMessage());
            }
        }

        // Fallback sur le contenu simple avec formatage HTML basique
        String content = notification.getContent();
        if (content == null) {
            content = notification.getTitle();
        }

        return content != null ? formatForWeb(content) : "";
    }

    private String processTemplate(String template, Map<String, String> parameters) {
        if (template == null || parameters == null) {
            return template;
        }

        String processed = template;
        for (Map.Entry<String, String> param : parameters.entrySet()) {
            String placeholder = "{{" + param.getKey() + "}}";
            String value = param.getValue() != null ? param.getValue() : "";
            processed = processed.replace(placeholder, value);
        }

        return processed;
    }

    private String formatForWeb(String content) {
        if (content == null) return "";

        // Formatage basique pour l'affichage web
        return content
                .replace("\n", "<br>")
                .replace("\t", "&nbsp;&nbsp;&nbsp;&nbsp;");
    }

    private boolean hasWebhookConfiguration() {
        // Vérifier si une URL de webhook est configurée
        return StringUtils.hasText(properties.getPush().getServerUrl());
    }

    private boolean hasWebPushConfiguration() {
        // Vérifier si les clés pour les notifications web push sont configurées
        return StringUtils.hasText(properties.getPush().getApiKey());
    }

    private void sendViaWebhook(Notification notification) throws Exception {
        if (!hasWebhookConfiguration()) return;

        try {
            String webhookUrl = properties.getPush().getServerUrl();

            Map<String, Object> payload = new HashMap<>();
            payload.put("notification", convertToWebNotification(notification));
            payload.put("recipient", notification.getRecipientId());
            payload.put("timestamp", System.currentTimeMillis());

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            if (StringUtils.hasText(properties.getPush().getApiKey())) {
                headers.setBearerAuth(properties.getPush().getApiKey());
            }

            HttpEntity<String> request = new HttpEntity<>(
                    objectMapper.writeValueAsString(payload),
                    headers
            );

            ResponseEntity<String> response = restTemplate.postForEntity(webhookUrl, request, String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                log.debug("Webhook envoyé avec succès pour la notification {}", notification.getId());
            } else {
                log.warn("Webhook retourné avec le statut {} pour la notification {}",
                        response.getStatusCode(), notification.getId());
            }

        } catch (Exception e) {
            log.error("Erreur lors de l'envoi du webhook pour la notification {}", notification.getId(), e);
            throw e;
        }
    }

    private void sendWebPushNotification(Notification notification) throws Exception {
        if (!hasWebPushConfiguration()) return;

        // Implémentation basique pour les Web Push Notifications
        // Vous pouvez intégrer ici une librairie comme webpush-java
        log.debug("Web Push notification simulée pour la notification {}", notification.getId());
    }

    private void startCacheCleanup() {
        // Nettoyage périodique du cache (peut être remplacé par un @Scheduled dans un autre service)
        java.util.concurrent.Executors.newSingleThreadScheduledExecutor()
                .scheduleAtFixedRate(this::cleanupCache, 1, 1, java.util.concurrent.TimeUnit.HOURS);
    }

    private void cleanupCache() {
        try {
            LocalDateTime cutoff = LocalDateTime.now().minusDays(7); // Garder 7 jours

            webNotificationCache.entrySet().removeIf(entry -> {
                entry.getValue().cleanup();
                return entry.getValue().isEmpty() || entry.getValue().getLastUpdate().isBefore(cutoff);
            });

            log.debug("Nettoyage du cache web effectué. Utilisateurs restants: {}",
                    webNotificationCache.size());

        } catch (Exception e) {
            log.error("Erreur lors du nettoyage du cache web", e);
        }
    }

    // Classe interne pour gérer le cache des notifications web par utilisateur
    private static class NotificationWebCache {
        private final java.util.concurrent.ConcurrentLinkedQueue<Map<String, Object>> notifications =
                new java.util.concurrent.ConcurrentLinkedQueue<>();
        private final java.util.concurrent.atomic.AtomicInteger unreadCount =
                new java.util.concurrent.atomic.AtomicInteger(0);
        private volatile LocalDateTime lastUpdate = LocalDateTime.now();

        public void addNotification(Map<String, Object> notification) {
            notifications.offer(notification);
            if (!(Boolean) notification.getOrDefault("isRead", false)) {
                unreadCount.incrementAndGet();
            }
            lastUpdate = LocalDateTime.now();

            // Garder seulement les 100 dernières notifications
            while (notifications.size() > 100) {
                Map<String, Object> removed = notifications.poll();
                if (removed != null && !(Boolean) removed.getOrDefault("isRead", false)) {
                    unreadCount.decrementAndGet();
                }
            }
        }

        public boolean markAsRead(Long notificationId) {
            for (Map<String, Object> notification : notifications) {
                if (notificationId.equals(notification.get("id"))) {
                    if (!(Boolean) notification.getOrDefault("isRead", false)) {
                        notification.put("isRead", true);
                        unreadCount.decrementAndGet();
                        lastUpdate = LocalDateTime.now();
                        return true;
                    }
                }
            }
            return false;
        }

        public java.util.List<Map<String, Object>> getNotifications() {
            return new java.util.ArrayList<>(notifications);
        }

        public int getUnreadCount() {
            return Math.max(0, unreadCount.get());
        }

        public LocalDateTime getLastUpdate() {
            return lastUpdate;
        }

        public boolean isEmpty() {
            return notifications.isEmpty();
        }

        public void cleanup() {
            // Supprimer les notifications trop anciennes (plus de 30 jours)
            LocalDateTime cutoff = LocalDateTime.now().minusDays(30);

            notifications.removeIf(notification -> {
                try {
                    LocalDateTime createdAt = LocalDateTime.parse((String) notification.get("createdAt"));
                    boolean shouldRemove = createdAt.isBefore(cutoff);

                    if (shouldRemove && !(Boolean) notification.getOrDefault("isRead", false)) {
                        unreadCount.decrementAndGet();
                    }

                    return shouldRemove;
                } catch (Exception e) {
                    return false; // En cas d'erreur, garder la notification
                }
            });
        }
    }
}