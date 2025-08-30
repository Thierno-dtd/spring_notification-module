package module.notification.repositories;

import module.notification.entities.NotificationRetry;
import module.notification.enums.ChannelType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface NotificationRetryRepository extends JpaRepository<NotificationRetry, Long> {

    /**
     * Trouve les retries programmés à traiter
     */
    List<NotificationRetry> findByStatusAndScheduledAtBefore(String status, LocalDateTime dateTime);

    /**
     * Compte les tentatives pour une notification et un canal donnés
     */
    int countByNotificationIdAndChannel(Long notificationId, ChannelType channel);

    /**
     * Trouve l'historique des retries pour une notification
     */
    List<NotificationRetry> findByNotificationIdOrderByCreatedAtDesc(Long notificationId);

    /**
     * Compte les retries par statut
     */
    long countByStatus(String status);

    /**
     * Supprime les anciens retries
     */
    @Modifying
    @Transactional
    @Query("DELETE FROM NotificationRetry r WHERE r.createdAt < :cutoffDate")
    int deleteByCreatedAtBefore(@Param("cutoffDate") LocalDateTime cutoffDate);

    /**
     * Trouve les retries échoués pour un canal spécifique
     */
    @Query("SELECT r FROM NotificationRetry r WHERE r.channel = :channel AND r.status = 'FAILED' AND r.createdAt > :since")
    List<NotificationRetry> findFailedRetriesForChannelSince(@Param("channel") ChannelType channel, @Param("since") LocalDateTime since);

    /**
     * Statistiques par canal
     */
    @Query("SELECT r.channel, r.status, COUNT(r) FROM NotificationRetry r GROUP BY r.channel, r.status")
    List<Object[]> getRetryStatisticsByChannelAndStatus();

    /**
     * Trouve les retries en cours de traitement depuis trop longtemps
     */
    @Query("SELECT r FROM NotificationRetry r WHERE r.status = 'PROCESSING' AND r.processedAt < :stuckThreshold")
    List<NotificationRetry> findStuckRetries(@Param("stuckThreshold") LocalDateTime stuckThreshold);
}