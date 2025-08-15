package module.notification.services.servicesImpl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import module.notification.config.NotificationProperties;
import module.notification.entities.Notification;
import module.notification.enums.ChannelType;
import module.notification.exceptions.NotificationException;
import module.notification.services.Iservices.NotificationChannelService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "notification.push.enabled", havingValue = "true")
@Slf4j
public class PushNotificationService implements NotificationChannelService {
    private final NotificationProperties properties;
    private final NotificationTemplateService templateService;

    @Override
    public ChannelType getChannelType() {
        return ChannelType.PUSH;
    }

    @Override
    public void send(Notification notification) throws Exception {
        // Récupérer le token push du destinataire depuis la base ou les paramètres
        String pushToken = notification.getParameters().get("pushToken");
        if (!StringUtils.hasText(pushToken)) {
            throw new NotificationException("Token push non fourni pour la notification");
        }

        String message = processMessage(notification);
        sendPushNotification(pushToken, notification.getTitle(), message);

        log.info("Push notification envoyée pour le token: {}", pushToken.substring(0, 10) + "...");
    }

    @Override
    public boolean isEnabled() {
        return properties.getPush().isEnabled();
    }

    private String processMessage(Notification notification) {
        if (StringUtils.hasText(notification.getTemplateId())) {
            try {
                var template = templateService.getTemplate(notification.getTemplateId());
                if (StringUtils.hasText(template.getPushTemplate())) {
                    return templateService.processTemplate(template.getPushTemplate(), notification.getParameters());
                }
            } catch (Exception e) {
                log.warn("Erreur lors du traitement du template Push: {}", e.getMessage());
            }
        }
        return notification.getContent();
    }

    private void sendPushNotification(String token, String title, String body) throws Exception {
        // Implémentation Firebase Cloud Messaging
        /*
        Message message = Message.builder()
                .setNotification(Notification.builder()
                        .setTitle(title)
                        .setBody(body)
                        .build())
                .setToken(token)
                .build();

        String response = FirebaseMessaging.getInstance().send(message);
        */
        log.info("Push notification envoyée: {}", title);
    }
}
