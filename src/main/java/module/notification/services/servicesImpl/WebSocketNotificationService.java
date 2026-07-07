package module.notification.services.servicesImpl;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import module.notification.entities.Notification;
import module.notification.enums.ChannelType;
import module.notification.services.Iservices.NotificationChannelService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "notification.websocket.enabled", havingValue = "true")
@Slf4j
public class WebSocketNotificationService implements NotificationChannelService {

    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public ChannelType getChannelType() {
        return ChannelType.WEBSOCKET;
    }

    @Override
    public void send(Notification notification) throws Exception {
        String destination = "/topic/notifications/" + notification.getRecipientId();

        var payload = objectMapper.createObjectNode();
        payload.put("id", notification.getId());
        payload.put("title", notification.getTitle());
        payload.put("content", notification.getContent());
        payload.put("type", notification.getType());
        payload.put("priority", notification.getPriority().name());
        payload.put("timestamp", notification.getCreatedAt().toString());

        messagingTemplate.convertAndSend(destination, payload.toString());

        log.info("WebSocket notification envoyée à {}", destination);
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}