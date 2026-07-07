package module.notification.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import module.notification.enums.ChannelType;
import module.notification.enums.NotificationPriority;

import java.time.LocalDateTime;

@Entity
@Table(name = "notification_metrics", indexes = {
        @Index(name = "idx_metrics_timestamp", columnList = "timestamp"),
        @Index(name = "idx_metrics_channel_type", columnList = "channel, type"),
        @Index(name = "idx_metrics_status_date", columnList = "status, date_created")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationMetric {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "notification_id")
    private Long notificationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false)
    private ChannelType channel;

    @Column(name = "type", nullable = false)
    private String type;

    @Enumerated(EnumType.STRING)
    @Column(name = "priority")
    private NotificationPriority priority;

    @Column(name = "status", nullable = false)
    private String status; // SENT, FAILED, READ, DELIVERED

    @Column(name = "recipient_id")
    private String recipientId;

    @Column(name = "sender_id")
    private String senderId;

    @Column(name = "processing_time_ms")
    private Long processingTimeMs;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "template_id")
    private String templateId;

    @Column(name = "timestamp", nullable = false)
    private LocalDateTime timestamp;

    @Column(name = "date_created", nullable = false)
    private LocalDateTime dateCreated;

    // Métadonnées supplémentaires
    @Column(name = "user_agent")
    private String userAgent;

    @Column(name = "ip_address")
    private String ipAddress;

    @Column(name = "device_type")
    private String deviceType;

    @Column(name = "retry_count")
    private Integer retryCount;

    @PrePersist
    void prePersist() {
        if (dateCreated == null) {
            dateCreated = LocalDateTime.now();
        }
        if (timestamp == null) {
            timestamp = LocalDateTime.now();
        }
        if (retryCount == null) {
            retryCount = 0;
        }
    }
}