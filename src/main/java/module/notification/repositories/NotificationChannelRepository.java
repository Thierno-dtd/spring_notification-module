package module.notification.repositories;

import module.notification.entities.NotificationChannel;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationChannelRepository extends JpaRepository<NotificationChannel, Long> {

}
