package module.notification.services.servicesImpl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import module.notification.config.NotificationProperties;
import module.notification.entities.Notification;
import module.notification.entities.NotificationTemplate;
import module.notification.entities.NotificationRecipient;
import module.notification.enums.ChannelType;
import module.notification.enums.NotificationPriority;
import module.notification.exceptions.NotificationException;
import module.notification.repositories.NotificationTemplateRepository;
import module.notification.services.Iservices.NotificationChannelService;
import module.notification.providers.Iproviders.PushNotificationProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import jakarta.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "notification.push.enabled", havingValue = "true")
@Slf4j
public class PushNotificationService implements NotificationChannelService {

    private final NotificationProperties properties;
    private final PushNotificationProvider pushProvider;
    private final NotificationTemplateRepository templateRepository;

    // Cache pour les tokens push des utilisateurs
    private final Map<String, String> userTokenCache = new ConcurrentHashMap<>();

    // Métriques internes
    private final AtomicLong sentCount = new AtomicLong(0);
    private final AtomicLong failedCount = new AtomicLong(0);
    private final Map<String, AtomicLong> topicSubscriptions = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        if (isEnabled() && !isConfigured()) {
            log.warn("Service Push activé mais mal configuré. Vérifiez les propriétés de configuration.");
        } else if (isEnabled()) {
            log.info("Service Push initialisé avec succès. Provider: {}", properties.getPush().getProvider());
            log.info("Configuration Push: Firebase={}, APNS={}, Generic={}",
                    isFirebaseConfigured(), isApnsConfigured(), isGenericConfigured());
        }
    }

    @Override
    public ChannelType getChannelType() {
        return ChannelType.PUSH;
    }

    @Override
    public void send(Notification notification) throws Exception {
        validateNotification(notification);

        try {
            String pushToken = resolvePushToken(notification);

            if (!StringUtils.hasText(pushToken)) {
                throw new NotificationException("Token push manquant pour le destinataire: " + notification.getRecipientId());
            }

            String title = processTitle(notification);
            String body = processContent(notification);
            Map<String, String> additionalData = buildAdditionalData(notification);

            // Personnaliser selon la priorité
            customizeForPriority(notification, additionalData);

            pushProvider.sendPushNotification(pushToken, title, body, additionalData);

            sentCount.incrementAndGet();
            cacheUserToken(notification.getRecipientId(), pushToken);

            log.info("Push notification envoyée avec succès au destinataire {} (token: {})",
                    notification.getRecipientId(), maskToken(pushToken));

        } catch (Exception e) {
            failedCount.incrementAndGet();
            log.error("Erreur lors de l'envoi de la push notification pour {}: {}",
                    notification.getRecipientId(), e.getMessage());
            throw new NotificationException("Échec de l'envoi push notification", e);
        }
    }

    @Override
    public boolean isEnabled() {
        return properties.getPush().isEnabled();
    }

    @Override
    public boolean isConfigured() {
        return pushProvider != null && pushProvider.isConfigured() && properties.isPushConfigured();
    }

    @Override
    public void validateNotification(Notification notification) {
        NotificationChannelService.super.validateNotification(notification);

        if (!StringUtils.hasText(notification.getRecipientId())) {
            throw new IllegalArgumentException("ID du destinataire requis pour les notifications push");
        }
    }

    @Override
    public boolean supportsPriority(NotificationPriority priority) {
        return true;
    }

    @Override
    public Map<String, Integer> getRateLimits() {
        return Map.of(
                "per_minute", properties.getRateLimit().getMaxPushPerHour() / 60,
                "per_hour", properties.getRateLimit().getMaxPushPerHour()
        );
    }

    @Override
    public boolean healthCheck() {
        try {
            boolean providerHealthy = pushProvider != null && pushProvider.isConfigured();
            boolean configurationValid = isConfigured();
            boolean metricsHealthy = sentCount.get() >= 0; // Simple check

            return providerHealthy && configurationValid && metricsHealthy && isEnabled();
        } catch (Exception e) {
            log.warn("Health check Push échoué: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public Map<String, Object> getMetrics() {
        // Create a new mutable HashMap from the parent metrics
        Map<String, Object> baseMetrics = new HashMap<>(NotificationChannelService.super.getMetrics());

        baseMetrics.put("push_provider", properties.getPush().getProvider());
        baseMetrics.put("sent_count", sentCount.get());
        baseMetrics.put("failed_count", failedCount.get());
        baseMetrics.put("success_rate", calculateSuccessRate());
        baseMetrics.put("cached_tokens", userTokenCache.size());
        baseMetrics.put("topic_subscriptions", getTopicSubscriptionsCount());

        // Métriques spécifiques au provider
        switch (properties.getPush().getProvider().toLowerCase()) {
            case "firebase":
                baseMetrics.put("firebase_project", properties.getPush().getFirebase().getProjectId());
                baseMetrics.put("firebase_configured", isFirebaseConfigured());
                break;
            case "apns":
                baseMetrics.put("apns_team_id", properties.getPush().getApns().getTeamId());
                baseMetrics.put("apns_production", properties.getPush().getApns().isProduction());
                baseMetrics.put("apns_configured", isApnsConfigured());
                break;
            case "generic":
                baseMetrics.put("generic_server", properties.getPush().getServerUrl());
                baseMetrics.put("generic_configured", isGenericConfigured());
                break;
        }

        return baseMetrics;
    }

    // ========== NOUVELLES FONCTIONNALITÉS ==========

    /**
     * Envoie une notification push à un topic/groupe
     */
    public void sendToTopic(String topic, Notification notification) throws Exception {
        validateNotification(notification);

        if (!StringUtils.hasText(topic)) {
            throw new NotificationException("Topic requis pour l'envoi au groupe");
        }

        try {
            String title = processTitle(notification);
            String body = processContent(notification);
            Map<String, String> additionalData = buildAdditionalData(notification);

            additionalData.put("topic", topic);

            pushProvider.sendPushNotificationToTopic(topic, title, body, additionalData);

            topicSubscriptions.computeIfAbsent(topic, k -> new AtomicLong(0)).incrementAndGet();
            sentCount.incrementAndGet();

            log.info("Push notification envoyée au topic '{}' avec succès", topic);

        } catch (Exception e) {
            failedCount.incrementAndGet();
            log.error("Erreur lors de l'envoi de push notification au topic '{}': {}", topic, e.getMessage());
            throw new NotificationException("Échec de l'envoi push notification au topic", e);
        }
    }

    /**
     * Envoie des notifications push en masse à plusieurs tokens
     */
    public Map<String, Boolean> sendBulkNotifications(List<String> tokens, Notification notification) throws Exception {
        validateNotification(notification);

        if (tokens == null || tokens.isEmpty()) {
            throw new NotificationException("Liste de tokens vide pour l'envoi en masse");
        }

        Map<String, Boolean> results = new HashMap<>();
        String title = processTitle(notification);
        String body = processContent(notification);
        Map<String, String> additionalData = buildAdditionalData(notification);

        for (String token : tokens) {
            try {
                if (StringUtils.hasText(token)) {
                    pushProvider.sendPushNotification(token, title, body, additionalData);
                    results.put(token, true);
                    sentCount.incrementAndGet();
                } else {
                    results.put(token, false);
                }
            } catch (Exception e) {
                log.warn("Échec d'envoi push pour le token {}: {}", maskToken(token), e.getMessage());
                results.put(token, false);
                failedCount.incrementAndGet();
            }
        }

        log.info("Envoi en masse terminé: {}/{} notifications push envoyées",
                results.values().stream().mapToInt(success -> success ? 1 : 0).sum(), tokens.size());

        return results;
    }

    /**
     * Enregistre ou met à jour un token push pour un utilisateur
     */
    public void registerUserToken(String userId, String pushToken) {
        if (StringUtils.hasText(userId) && StringUtils.hasText(pushToken)) {
            userTokenCache.put(userId, pushToken);
            log.debug("Token push enregistré pour l'utilisateur: {}", userId);
        }
    }

    /**
     * Supprime le token push d'un utilisateur
     */
    public void unregisterUserToken(String userId) {
        if (userTokenCache.remove(userId) != null) {
            log.debug("Token push supprimé pour l'utilisateur: {}", userId);
        }
    }

    /**
     * Récupère le token push d'un utilisateur depuis le cache
     */
    public String getUserToken(String userId) {
        return userTokenCache.get(userId);
    }

    /**
     * Envoie une notification de test
     */
    public boolean sendTestNotification(String userId, String pushToken) {
        try {
            Notification testNotification = createTestNotification(userId);

            if (StringUtils.hasText(pushToken)) {
                testNotification.getParameters().put("pushToken", pushToken);
            }

            send(testNotification);
            return true;
        } catch (Exception e) {
            log.error("Échec du test de notification push pour {}: {}", userId, e.getMessage());
            return false;
        }
    }

    /**
     * Nettoie le cache des tokens expirés
     */
    public void cleanupTokenCache() {
        // Pour une implémentation plus sophistiquée, vous pourriez vérifier
        // la validité des tokens auprès du provider
        int initialSize = userTokenCache.size();

        // Logique de nettoyage basique - ici on pourrait vérifier la dernière utilisation
        // Pour l'instant, on garde tous les tokens car on n'a pas de timestamp

        log.debug("Nettoyage du cache de tokens: {} tokens conservés", userTokenCache.size());
    }

    /**
     * Obtient les statistiques détaillées
     */
    public Map<String, Object> getDetailedStats() {
        Map<String, Object> stats = new HashMap<>();

        stats.put("total_sent", sentCount.get());
        stats.put("total_failed", failedCount.get());
        stats.put("success_rate", calculateSuccessRate());
        stats.put("cached_tokens", userTokenCache.size());
        stats.put("active_topics", topicSubscriptions.size());
        stats.put("total_topic_messages", getTopicSubscriptionsCount());
        stats.put("provider", properties.getPush().getProvider());
        stats.put("enabled", isEnabled());
        stats.put("configured", isConfigured());
        stats.put("last_cleanup", LocalDateTime.now().toString());

        return stats;
    }

    // ========== MÉTHODES PRIVÉES UTILITAIRES ==========

    private String resolvePushToken(Notification notification) {
        // Priorité 1: Token dans les paramètres
        if (notification.getParameters() != null && notification.getParameters().containsKey("pushToken")) {
            return notification.getParameters().get("pushToken");
        }

        // Priorité 2: Token depuis le cache utilisateur
        String cachedToken = userTokenCache.get(notification.getRecipientId());
        if (StringUtils.hasText(cachedToken)) {
            return cachedToken;
        }

        // Priorité 3: Token dans les métadonnées (parsing JSON basique)
        if (StringUtils.hasText(notification.getMetadata())) {
            String tokenFromMetadata = extractTokenFromMetadata(notification.getMetadata());
            if (StringUtils.hasText(tokenFromMetadata)) {
                return tokenFromMetadata;
            }
        }

        return null;
    }

    private String extractTokenFromMetadata(String metadata) {
        try {
            if (metadata.contains("pushToken")) {
                String[] parts = metadata.split("pushToken\":");
                if (parts.length > 1) {
                    return parts[1].split(",")[0].replace("\"", "").trim();
                }
            }
        } catch (Exception e) {
            log.warn("Erreur lors de l'extraction du token depuis les métadonnées", e);
        }
        return null;
    }

    private String processTitle(Notification notification) {
        return notification.getTitle() != null ? notification.getTitle() : "Notification";
    }

    private String processContent(Notification notification) {
        if (StringUtils.hasText(notification.getTemplateId())) {
            try {
                NotificationTemplate template = templateRepository
                        .findByIdAndIsActiveTrue(notification.getTemplateId())
                        .orElse(null);

                if (template != null && StringUtils.hasText(template.getPushTemplate())) {
                    return processTemplate(template.getPushTemplate(), notification.getParameters());
                }
            } catch (Exception e) {
                log.warn("Erreur lors du traitement du contenu Push via template {}: {}",
                        notification.getTemplateId(), e.getMessage());
            }
        }

        String content = notification.getContent();
        return content != null ? content : notification.getTitle();
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

    private Map<String, String> buildAdditionalData(Notification notification) {
        Map<String, String> data = new HashMap<>();

        data.put("notificationId", String.valueOf(notification.getId()));
        data.put("type", notification.getType().name());
        data.put("priority", notification.getPriority().name());
        data.put("recipientId", notification.getRecipientId());
        data.put("timestamp", String.valueOf(System.currentTimeMillis()));

        if (notification.getParameters() != null) {
            notification.getParameters().forEach((key, value) -> {
                if (!"pushToken".equals(key)) { // Exclure le token des données
                    data.put(key, value);
                }
            });
        }

        if (StringUtils.hasText(notification.getExternalId())) {
            data.put("externalId", notification.getExternalId());
        }

        return data;
    }

    private void customizeForPriority(Notification notification, Map<String, String> additionalData) {
        NotificationPriority priority = notification.getPriority();
        if (priority != null) {
            switch (priority) {
                case URGENT:
                    additionalData.put("android_priority", "high");
                    additionalData.put("ios_priority", "10");
                    additionalData.put("ttl", "3600"); // 1 heure
                    break;
                case HIGH:
                    additionalData.put("android_priority", "high");
                    additionalData.put("ios_priority", "10");
                    additionalData.put("ttl", "7200"); // 2 heures
                    break;
                case MEDIUM:
                    additionalData.put("android_priority", "normal");
                    additionalData.put("ios_priority", "5");
                    additionalData.put("ttl", "86400"); // 24 heures
                    break;
                case LOW:
                    additionalData.put("android_priority", "normal");
                    additionalData.put("ios_priority", "5");
                    additionalData.put("ttl", "604800"); // 7 jours
                    break;
            }
        }
    }

    private void cacheUserToken(String userId, String token) {
        userTokenCache.put(userId, token);
    }

    private String maskToken(String token) {
        if (token == null || token.length() < 10) {
            return "***";
        }
        return token.substring(0, 4) + "***" + token.substring(token.length() - 4);
    }

    private double calculateSuccessRate() {
        long total = sentCount.get() + failedCount.get();
        return total > 0 ? (double) sentCount.get() / total * 100 : 0.0;
    }

    private long getTopicSubscriptionsCount() {
        return topicSubscriptions.values().stream()
                .mapToLong(AtomicLong::get)
                .sum();
    }

    private Notification createTestNotification(String userId) {
        return Notification.builder()
                .id(System.currentTimeMillis())
                .title("Test Push Notification")
                .content("Ceci est une notification de test pour vérifier le fonctionnement du service push.")
                .type(module.notification.enums.NotificationType.SYSTEM)
                .priority(NotificationPriority.MEDIUM)
                .status(module.notification.enums.NotificationStatus.PENDING)
                .recipientId(userId)
                .channels(Set.of(ChannelType.PUSH))
                .createdAt(LocalDateTime.now())
                .parameters(new HashMap<>())
                .build();
    }

    // Méthodes de vérification de configuration
    private boolean isFirebaseConfigured() {
        NotificationProperties.Push.Firebase firebase = properties.getPush().getFirebase();
        return StringUtils.hasText(firebase.getProjectId()) &&
                (StringUtils.hasText(firebase.getCredentialsJson()) ||
                        StringUtils.hasText(firebase.getCredentialsPath()));
    }

    private boolean isApnsConfigured() {
        NotificationProperties.Push.Apns apns = properties.getPush().getApns();
        return StringUtils.hasText(apns.getKeyId()) &&
                StringUtils.hasText(apns.getTeamId()) &&
                StringUtils.hasText(apns.getKeyPath());
    }

    private boolean isGenericConfigured() {
        return StringUtils.hasText(properties.getPush().getServerUrl()) &&
                StringUtils.hasText(properties.getPush().getApiKey());
    }
}