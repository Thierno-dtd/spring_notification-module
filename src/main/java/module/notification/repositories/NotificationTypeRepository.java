package module.notification.repositories;

import module.notification.entities.NotificationType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NotificationTypeRepository extends JpaRepository<NotificationType, String> {
    List<NotificationType> findByIsActiveTrue();
}