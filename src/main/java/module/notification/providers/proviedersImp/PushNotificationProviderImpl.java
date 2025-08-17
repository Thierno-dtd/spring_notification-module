package module.notification.providers.proviedersImp;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import module.notification.config.NotificationProperties;
import module.notification.providers.Iproviders.PushNotificationProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.*;

import java.util.Map;
import java.util.HashMap;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "notification.push.enabled", havingValue = "true")
@Slf4j
public class PushNotificationProviderImpl implements PushNotificationProvider {

    private final NotificationProperties properties;
    private final RestTemplate restTemplate = new RestTemplate();

    @Override
    public void sendPushNotification(String token, String title, String body, Map<String, String> data) throws Exception {
        validateConfiguration();

        Map<String, Object> payload = createPayload(title, body, data);
        payload.put("token", token);

        sendNotification(payload);
        log.info("Push notification envoyée avec succès au token: {}", maskToken(token));
    }

    @Override
    public void sendPushNotificationToTopic(String topic, String title, String body, Map<String, String> data) throws Exception {
        validateConfiguration();

        Map<String, Object> payload = createPayload(title, body, data);
        payload.put("topic", topic);

        sendNotification(payload);
        log.info("Push notification envoyée au topic '{}' avec succès", topic);
    }

    @Override
    public boolean isConfigured() {
        return StringUtils.hasText(properties.getPush().getFirebase().getProjectId()) ||
                StringUtils.hasText(properties.getPush().getApns().getKeyId());
    }

    private Map<String, Object> createPayload(String title, String body, Map<String, String> data) {
        Map<String, Object> payload = new HashMap<>();

        Map<String, String> notification = new HashMap<>();
        notification.put("title", title);
        notification.put("body", body);
        payload.put("notification", notification);

        if (data != null && !data.isEmpty()) {
            payload.put("data", data);
        }

        return payload;
    }

    private void sendNotification(Map<String, Object> payload) throws Exception {
        // Implémentation générique - à adapter selon le provider
        // Cette méthode devrait être surchargée selon le service utilisé
        log.info("Simulation d'envoi push notification: {}", payload);

        // Pour une implémentation réelle, utiliser le service approprié:
        // - Firebase Cloud Messaging
        // - Apple Push Notification Service
        // - OneSignal
        // - Pusher
        // etc.
    }

    private void validateConfiguration() {
        if (!isConfigured()) {
            throw new IllegalStateException("Push notification service non configuré");
        }
    }

    private String maskToken(String token) {
        if (token == null || token.length() < 10) {
            return "***";
        }
        return token.substring(0, 4) + "***" + token.substring(token.length() - 4);
    }
}