package module.notification.events;

import module.notification.entities.Notification;

public class NotificationSentEvent extends NotificationEvent {

    public NotificationSentEvent(Object source, Notification notification) {
        super(source, notification);
    }
}
