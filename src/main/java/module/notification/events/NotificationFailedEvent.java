package module.notification.events;

import lombok.Getter;
import module.notification.entities.Notification;

@Getter
public class NotificationFailedEvent extends NotificationEvent {

    private final String errorMessage;
    private final Exception exception;

    public NotificationFailedEvent(Object source, Notification notification, String errorMessage, Exception exception) {
        super(source, notification);
        this.errorMessage = errorMessage;
        this.exception = exception;
    }
}
