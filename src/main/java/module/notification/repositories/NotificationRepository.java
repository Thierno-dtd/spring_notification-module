package module.notification.repositories;

import module.notification.entities.Notification;
import module.notification.enums.NotificationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    Page<Notification> findByRecipientIdOrderByCreatedAtDesc(String recipientId, Pageable pageable);

    Optional<Notification> findByIdAndRecipientId(Long id, String recipientId);

    Long countByRecipientIdAndReadAtIsNull(String recipientId);

    List<Notification> findByStatusAndScheduledAtBefore(NotificationStatus status, LocalDateTime dateTime);

    @Query("SELECT n FROM Notification n WHERE n.recipientId = :recipientId AND n.readAt IS NULL")
    List<Notification> findUnreadNotifications(@Param("recipientId") String recipientId);

    @Modifying
    @Query("UPDATE Notification n SET n.status = :status WHERE n.id IN :ids")
    void updateStatusForIds(@Param("ids") List<Long> ids, @Param("status") NotificationStatus status);

    @Modifying
    @Query("DELETE FROM Notification n WHERE n.createdAt < :cutoffDate AND n.status = :status")
    int deleteByCreatedAtBeforeAndStatus(@Param("cutoffDate") LocalDateTime cutoffDate,
                                          @Param("status") NotificationStatus status);
}
