package module.notification.events;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class NotificationEventListener {
    @EventListener
    @Async
    public void handleNotificationSent(NotificationSentEvent event) {
        log.info("Notification envoyée - ID: {}, Destinataire: {}",
                event.getNotification().getId(),
                event.getNotification().getRecipientId());

        // Ici vous pouvez ajouter d'autres traitements :
        // - Metrics/Monitoring
        // - Audit logs
        // - Webhooks
        // - Intégrations tierces
    }

    @EventListener
    @Async
    public void handleNotificationRead(NotificationReadEvent event) {
        log.info("Notification lue - ID: {}, Destinataire: {}",
                event.getNotification().getId(),
                event.getNotification().getRecipientId());

        // Traitements post-lecture
        // - Analytics
        // - Engagement tracking
    }

    @EventListener
    @Async
    public void handleNotificationFailed(NotificationFailedEvent event) {
        log.error("Échec d'envoi de notification - ID: {}, Erreur: {}",
                event.getNotification().getId(),
                event.getErrorMessage(),
                event.getException());

        // Traitements d'erreur
        // - Retry logic
        // - Alert system
        // - Error reporting
    }
}
