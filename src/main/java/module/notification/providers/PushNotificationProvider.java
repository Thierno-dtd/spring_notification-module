package module.notification.providers;

import java.util.Map;

public interface PushNotificationProvider {
    void sendPushNotification(String token, String title, String body, Map<String, String> data) throws Exception;
    void sendPushNotificationToTopic(String topic, String title, String body, Map<String, String> data) throws Exception;
    boolean isConfigured();
}