package module.notification.events;

import lombok.Getter;
import module.notification.entities.Notification;
import org.springframework.context.ApplicationEvent;

@Getter
public abstract class NotificationEvent extends ApplicationEvent {

    private final Notification notification;

    public NotificationEvent(Object source, Notification notification) {
        super(source);
        this.notification = notification;
    }
}
