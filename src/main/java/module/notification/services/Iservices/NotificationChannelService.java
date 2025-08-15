package module.notification.services.Iservices;

import module.notification.entities.Notification;
import module.notification.enums.ChannelType;

public interface NotificationChannelService {
    ChannelType getChannelType();
    void send(Notification notification) throws Exception;
    boolean isEnabled();
    default int getRetryCount() { return 3; }
    default int getRetryDelayMinutes() { return 5; }
}
