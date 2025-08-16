package module.notification.providers.proviedersImp;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import module.notification.config.NotificationProperties;
import module.notification.providers.Iproviders.PushNotificationProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Map;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "notification.push.enabled", havingValue = "true")
@Slf4j
public class PushNotificationProviderImpl implements PushNotificationProvider {

    private final NotificationProperties properties;

    @Override
    public void sendPushNotification(String token, String title, String body, Map<String, String> data) throws Exception {
        Message.Builder messageBuilder = Message.builder()
                .setToken(token)
                .setNotification(Notification.builder()
                        .setTitle(title)
                        .setBody(body)
                        .build());

        if (data != null && !data.isEmpty()) {
            messageBuilder.putAllData(data);
        }

        String response = FirebaseMessaging.getInstance().send(messageBuilder.build());
        log.info("Push notification envoyée avec succès. Message ID: {}", response);
    }

    @Override
    public void sendPushNotificationToTopic(String topic, String title, String body, Map<String, String> data) throws Exception {
        Message.Builder messageBuilder = Message.builder()
                .setTopic(topic)
                .setNotification(Notification.builder()
                        .setTitle(title)
                        .setBody(body)
                        .build());

        if (data != null && !data.isEmpty()) {
            messageBuilder.putAllData(data);
        }

        String response = FirebaseMessaging.getInstance().send(messageBuilder.build());
        log.info("Push notification envoyée au topic '{}' avec succès. Message ID: {}", topic, response);
    }

    @Override
    public boolean isConfigured() {
        return StringUtils.hasText(properties.getPush().getFirebaseServerKey());
    }
}