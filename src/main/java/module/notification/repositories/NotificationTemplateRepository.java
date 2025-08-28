package module.notification.repositories;

import module.notification.entities.NotificationTemplate;
import module.notification.enums.NotificationType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface NotificationTemplateRepository extends JpaRepository<NotificationTemplate, String> {
    List<NotificationTemplate> findByIsActiveTrue();

    List<NotificationTemplate> findByTypeAndIsActiveTrue(NotificationType type);

    Optional<NotificationTemplate> findByIdAndIsActiveTrue(String id);

    @Query("SELECT t FROM NotificationTemplate t WHERE t.name LIKE %:name% AND t.isActive = true")
    List<NotificationTemplate> findByNameContainingIgnoreCaseAndIsActiveTrue(@Param("name") String name);

}
