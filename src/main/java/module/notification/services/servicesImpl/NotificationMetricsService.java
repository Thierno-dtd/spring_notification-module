package module.notification.services.servicesImpl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import module.notification.enums.ChannelType;
import module.notification.enums.NotificationType;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationMetricsService {

    // Métriques et statistiques des notifications
    public void recordNotificationSent(ChannelType channel, NotificationType type) {
        // Implémenter l'enregistrement des métriques
    }

    public void recordNotificationDelivered(ChannelType channel, NotificationType type) {
        // Implémenter l'enregistrement des métriques
    }

    public void recordNotificationRead(ChannelType channel, NotificationType type) {
        // Implémenter l'enregistrement des métriques
    }

    public void recordNotificationFailed(ChannelType channel, NotificationType type) {
        // Implémenter l'enregistrement des métriques
    }
}
