package module.notification.repositories;

import module.notification.entities.NotificationMetric;
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
public interface NotificationMetricRepository extends JpaRepository<NotificationMetric, Long> {

    /**
     * Statistiques par canal pour une période donnée
     */
    @Query("SELECT m.channel, m.status, COUNT(m) FROM NotificationMetric m " +
            "WHERE m.timestamp BETWEEN :startDate AND :endDate " +
            "GROUP BY m.channel, m.status")
    List<Object[]> getChannelStatistics(@Param("startDate") LocalDateTime startDate,
                                        @Param("endDate") LocalDateTime endDate);

    /**
     * Statistiques par type de notification
     */
    @Query("SELECT m.type, m.status, COUNT(m) FROM NotificationMetric m " +
            "WHERE m.timestamp BETWEEN :startDate AND :endDate " +
            "GROUP BY m.type, m.status")
    List<Object[]> getTypeStatistics(@Param("startDate") LocalDateTime startDate,
                                     @Param("endDate") LocalDateTime endDate);

    /**
     * Temps de traitement moyen par canal
     */
    @Query("SELECT m.channel, AVG(m.processingTimeMs) FROM NotificationMetric m " +
            "WHERE m.processingTimeMs IS NOT NULL AND m.timestamp BETWEEN :startDate AND :endDate " +
            "GROUP BY m.channel")
    List<Object[]> getAverageProcessingTimeByChannel(@Param("startDate") LocalDateTime startDate,
                                                     @Param("endDate") LocalDateTime endDate);

    /**
     * Top erreurs par fréquence
     */
    @Query("SELECT m.errorMessage, COUNT(m) as errorCount FROM NotificationMetric m " +
            "WHERE m.status = 'FAILED' AND m.timestamp BETWEEN :startDate AND :endDate " +
            "GROUP BY m.errorMessage ORDER BY errorCount DESC")
    List<Object[]> getTopErrors(@Param("startDate") LocalDateTime startDate,
                                @Param("endDate") LocalDateTime endDate);

    /**
     * Statistiques par heure pour les dashboards
     */
    @Query("SELECT FUNCTION('HOUR', m.timestamp) as hour, m.status, COUNT(m) FROM NotificationMetric m " +
            "WHERE m.timestamp BETWEEN :startDate AND :endDate " +
            "GROUP BY FUNCTION('HOUR', m.timestamp), m.status " +
            "ORDER BY FUNCTION('HOUR', m.timestamp)")
    List<Object[]> getHourlyStatistics(@Param("startDate") LocalDateTime startDate,
                                       @Param("endDate") LocalDateTime endDate);

    /**
     * Utilisateurs les plus actifs
     */
    @Query("SELECT m.recipientId, COUNT(m) as notificationCount FROM NotificationMetric m " +
            "WHERE m.timestamp BETWEEN :startDate AND :endDate " +
            "GROUP BY m.recipientId ORDER BY notificationCount DESC")
    List<Object[]> getTopRecipients(@Param("startDate") LocalDateTime startDate,
                                    @Param("endDate") LocalDateTime endDate,
                                    org.springframework.data.domain.Pageable pageable);

    /**
     * Taux de succès par canal
     */
    @Query("SELECT m.channel, " +
            "SUM(CASE WHEN m.status = 'SENT' THEN 1 ELSE 0 END) as sent, " +
            "SUM(CASE WHEN m.status = 'FAILED' THEN 1 ELSE 0 END) as failed, " +
            "COUNT(m) as total FROM NotificationMetric m " +
            "WHERE m.timestamp BETWEEN :startDate AND :endDate " +
            "GROUP BY m.channel")
    List<Object[]> getSuccessRateByChannel(@Param("startDate") LocalDateTime startDate,
                                           @Param("endDate") LocalDateTime endDate);

    /**
     * Métriques pour un template spécifique
     */
    @Query("SELECT m.status, COUNT(m) FROM NotificationMetric m " +
            "WHERE m.templateId = :templateId AND m.timestamp BETWEEN :startDate AND :endDate " +
            "GROUP BY m.status")
    List<Object[]> getTemplateMetrics(@Param("templateId") String templateId,
                                      @Param("startDate") LocalDateTime startDate,
                                      @Param("endDate") LocalDateTime endDate);

    /**
     * Compte les notifications par statut dans une période
     */
    long countByStatusAndTimestampBetween(String status, LocalDateTime startDate, LocalDateTime endDate);

    /**
     * Compte par canal et statut
     */
    long countByChannelAndStatusAndTimestampBetween(ChannelType channel, String status,
                                                    LocalDateTime startDate, LocalDateTime endDate);

    /**
     * Supprime les anciennes métriques
     */
    @Modifying
    @Transactional
    @Query("DELETE FROM NotificationMetric m WHERE m.dateCreated < :cutoffDate")
    int deleteByDateCreatedBefore(@Param("cutoffDate") LocalDateTime cutoffDate);

    /**
     * Métriques de performance par device type
     */
    @Query("SELECT m.deviceType, AVG(m.processingTimeMs), COUNT(m) FROM NotificationMetric m " +
            "WHERE m.deviceType IS NOT NULL AND m.timestamp BETWEEN :startDate AND :endDate " +
            "GROUP BY m.deviceType")
    List<Object[]> getPerformanceByDeviceType(@Param("startDate") LocalDateTime startDate,
                                              @Param("endDate") LocalDateTime endDate);
}