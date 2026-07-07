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
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    Page<Notification> findByRecipientIdOrderByCreatedAtDesc(String recipientId, Pageable pageable);

    Optional<Notification> findByIdAndRecipientId(Long id, String recipientId);

    Long countByRecipientIdAndReadAtIsNull(String recipientId);

    /**
     * Trouve les notifications programmées à traiter
     */

    List<Notification> findByStatusAndScheduledAtBefore(NotificationStatus status, LocalDateTime dateTime);

    /**
     * Trouve les notifications non lues pour un destinataire
     */

    @Query("SELECT n FROM Notification n WHERE n.recipientId = :recipientId AND n.readAt IS NULL")
    List<Notification> findUnreadNotifications(@Param("recipientId") String recipientId);

    @Modifying
    @Query("UPDATE Notification n SET n.status = :status WHERE n.id IN :ids")
    void updateStatusForIds(@Param("ids") List<Long> ids, @Param("status") NotificationStatus status);

    /**
     * Supprime les anciennes notifications lues
     */

    @Modifying
    @Query("DELETE FROM Notification n WHERE n.createdAt < :cutoffDate AND n.status = :status")
    int deleteByCreatedAtBeforeAndStatus(@Param("cutoffDate") LocalDateTime cutoffDate,
                                          @Param("status") NotificationStatus status);


    /**
     * Trouve les notifications par statut
     */
    List<Notification> findByStatus(NotificationStatus status);

    /**
     * Trouve les notifications par destinataire et statut
     */
    List<Notification> findByRecipientIdAndStatus(String recipientId, NotificationStatus status);

    /**
     * Trouve les notifications par destinataire dans une période
     */
    @Query("SELECT n FROM Notification n WHERE n.recipientId = :recipientId AND n.createdAt BETWEEN :startDate AND :endDate ORDER BY n.createdAt DESC")
    List<Notification> findByRecipientIdAndCreatedAtBetween(@Param("recipientId") String recipientId,
                                                            @Param("startDate") LocalDateTime startDate,
                                                            @Param("endDate") LocalDateTime endDate);

    /**
     * Trouve les notifications par type
     */
    List<Notification> findByType(String type);

    /**
     * Trouve les notifications par priorité
     */
    List<Notification> findByPriority(module.notification.enums.NotificationPriority priority);

    /**
     * Trouve les notifications par ID externe
     */
    Optional<Notification> findByExternalId(String externalId);

    /**
     * Trouve les notifications échouées à reprendre
     */
    @Query("SELECT n FROM Notification n WHERE n.status = 'FAILED' AND n.createdAt > :since ORDER BY n.createdAt ASC")
    List<Notification> findFailedNotificationsSince(@Param("since") LocalDateTime since);

    /**
     * Compte les notifications par statut pour un destinataire
     */
    long countByRecipientIdAndStatus(String recipientId, NotificationStatus status);

    /**
     * Trouve les notifications récentes pour un destinataire
     */
    @Query("SELECT n FROM Notification n WHERE n.recipientId = :recipientId AND n.createdAt > :since ORDER BY n.createdAt DESC")
    List<Notification> findRecentNotificationsByRecipient(@Param("recipientId") String recipientId,
                                                          @Param("since") LocalDateTime since);

    /**
     * Met à jour le statut des notifications en lot
     */
    @Modifying
    @Transactional
    @Query("UPDATE Notification n SET n.status = :newStatus WHERE n.id IN :ids")
    void updateStatusBatch(@Param("ids") List<Long> ids, @Param("newStatus") NotificationStatus newStatus);

    /**
     * Trouve les notifications par template
     */
    List<Notification> findByTemplateId(String templateId);

    /**
     * Compte les notifications envoyées dans une période
     */
    @Query("SELECT COUNT(n) FROM Notification n WHERE n.status = 'SENT' AND n.sentAt BETWEEN :startDate AND :endDate")
    long countSentNotificationsBetween(@Param("startDate") LocalDateTime startDate,
                                       @Param("endDate") LocalDateTime endDate);

    /**
     * Trouve les notifications en attente de traitement
     */
    @Query("SELECT n FROM Notification n WHERE n.status IN ('PENDING', 'PROCESSING') ORDER BY n.createdAt ASC")
    List<Notification> findPendingNotifications();

    /**
     * Marque toutes les notifications comme lues pour un destinataire
     */
    @Modifying
    @Transactional
    @Query("UPDATE Notification n SET n.readAt = :readAt, n.status = 'READ' WHERE n.recipientId = :recipientId AND n.readAt IS NULL")
    int markAllAsReadForRecipient(@Param("recipientId") String recipientId, @Param("readAt") LocalDateTime readAt);

}
