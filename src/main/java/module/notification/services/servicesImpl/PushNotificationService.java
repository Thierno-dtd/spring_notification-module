package module.notification.services.servicesImpl;

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
import module.notification.providers.Iproviders.PushNotificationProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import jakarta.annotation.PostConstruct;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "notification.push.enabled", havingValue = "true")
@Slf4j
public class PushNotificationService implements NotificationChannelService {

    private final NotificationProperties properties;
    private final PushNotificationProvider pushProvider;
    private final NotificationTemplateRepository templateRepository;

    @PostConstruct
    public void init() {
        if (isEnabled() && !isConfigured()) {
            log.warn("Service Push activé mais mal configuré. Vérifiez les propriétés de configuration.");
        } else if (isEnabled()) {
            log.info("Service Push initialisé avec succès. Provider: {}",
                    properties.getPush().getProvider());
        }
    }

    @Override
    public ChannelType getChannelType() {
        return ChannelType.PUSH;
    }

    @Override
    public void send(Notification notification) throws Exception {
        validateNotification(notification);

        // Récupérer le token push du destinataire depuis les métadonnées ou un service externe
        String pushToken = extractPushToken(notification);

        if (!StringUtils.hasText(pushToken)) {
            throw new NotificationException("Token push manquant pour le destinataire: " + notification.getRecipientId());
        }

        try {
            String title = notification.getTitle();
            String body = processContent(notification);
            Map<String, String> additionalData = buildAdditionalData(notification);

            pushProvider.sendPushNotification(pushToken, title, body, additionalData);

            log.info("Push notification envoyée avec succès au destinataire {} (token: {})",
                    notification.getRecipientId(), maskToken(pushToken));

        } catch (Exception e) {
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
        return pushProvider != null && pushProvider.isConfigured();
    }

    @Override
    public void validateNotification(Notification notification) {
        NotificationChannelService.super.validateNotification(notification);

        // La validation du token push se fait dans send() car il peut être récupéré dynamiquement
    }

    @Override
    public boolean supportsPriority(NotificationPriority priority) {
        // Les push notifications supportent généralement les priorités
        return true;
    }

    @Override
    public Map<String, Integer> getRateLimits() {
        return Map.of(
                "per_minute", 60,  // Limite assez élevée pour les push
                "per_hour", 1000
        );
    }

    @Override
    public boolean healthCheck() {
        try {
            return isConfigured() && isEnabled() && pushProvider.isConfigured();
        } catch (Exception e) {
            log.warn("Health check Push échoué: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public Map<String, Object> getMetrics() {
        Map<String, Object> baseMetrics = NotificationChannelService.super.getMetrics();
        baseMetrics.put("push_provider", properties.getPush().getProvider());
        baseMetrics.put("firebase_project", properties.getPush().getFirebase().getProjectId());
        return baseMetrics;
    }

    // Méthodes utilitaires privées

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

        // Fallback sur le contenu simple
        String content = notification.getContent();
        if (content == null) {
            content = notification.getTitle();
        }

        return content != null ? content : "";
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

    private String extractPushToken(Notification notification) {
        // Priorité 1: Token dans les paramètres de la notification
        if (notification.getParameters() != null && notification.getParameters().containsKey("pushToken")) {
            return notification.getParameters().get("pushToken");
        }

        // Priorité 2: Token dans les métadonnées
        if (StringUtils.hasText(notification.getMetadata())) {
            try {
                // Parser les métadonnées JSON pour extraire le token
                // Implémentation simplifiée - vous pouvez utiliser Jackson pour parser le JSON
                if (notification.getMetadata().contains("pushToken")) {
                    // Extraction simple - à améliorer selon votre format de métadonnées
                    String[] parts = notification.getMetadata().split("pushToken\":");
                    if (parts.length > 1) {
                        String tokenPart = parts[1].split(",")[0].replace("\"", "").trim();
                        return tokenPart;
                    }
                }
            } catch (Exception e) {
                log.warn("Erreur lors de l'extraction du token push depuis les métadonnées", e);
            }
        }

        // Priorité 3: Récupérer depuis un service externe (NotificationRecipient)
        // Ici vous pourriez injecter un service pour récupérer le token depuis la base de données
        // return recipientService.getPushToken(notification.getRecipientId());

        return null;
    }

    private Map<String, String> buildAdditionalData(Notification notification) {
        Map<String, String> data = new HashMap<>();

        // Données de base
        data.put("notificationId", String.valueOf(notification.getId()));
        data.put("type", notification.getType().name());
        data.put("priority", notification.getPriority().name());
        data.put("recipientId", notification.getRecipientId());

        // Ajouter les paramètres personnalisés
        if (notification.getParameters() != null) {
            data.putAll(notification.getParameters());
        }

        // ID externe pour le tracking
        if (StringUtils.hasText(notification.getExternalId())) {
            data.put("externalId", notification.getExternalId());
        }

        // Timestamp
        data.put("timestamp", String.valueOf(System.currentTimeMillis()));

        return data;
    }

    private String maskToken(String token) {
        if (token == null || token.length() < 10) {
            return "***";
        }
        return token.substring(0, 4) + "***" + token.substring(token.length() - 4);
    }

    /**
     * Méthode pour envoyer une push notification à un topic/groupe
     */
    public void sendToTopic(String topic, Notification notification) throws Exception {
        try {
            String title = notification.getTitle();
            String body = processContent(notification);
            Map<String, String> additionalData = buildAdditionalData(notification);

            pushProvider.sendPushNotificationToTopic(topic, title, body, additionalData);

            log.info("Push notification envoyée au topic '{}' avec succès", topic);

        } catch (Exception e) {
            log.error("Erreur lors de l'envoi de push notification au topic '{}': {}", topic, e.getMessage());
            throw new NotificationException("Échec de l'envoi push notification au topic", e);
        }
    }
}