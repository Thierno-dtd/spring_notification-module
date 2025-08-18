package module.notification.events;

import module.notification.entities.Notification;

public class NotificationReadEvent extends NotificationEvent {

    public NotificationReadEvent(Object source, Notification notification) {
        super(source, notification);
    }
}
