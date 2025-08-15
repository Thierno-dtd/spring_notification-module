package module.notification.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import module.notification.enums.ChannelType;
import module.notification.enums.NotificationPriority;
import module.notification.enums.NotificationStatus;
import module.notification.enums.NotificationType;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Entity
@Table(name = "notifications")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String content;

    @Enumerated(EnumType.STRING)
    private NotificationType type;

    @Enumerated(EnumType.STRING)
    private NotificationPriority priority;

    @Enumerated(EnumType.STRING)
    private NotificationStatus status;

    @Column(name = "recipient_id")
    private String recipientId;

    @Column(name = "recipient_email")
    private String recipientEmail;

    @Column(name = "recipient_phone")
    private String recipientPhone;

    @Column(name = "sender_id")
    private String senderId;

    @ElementCollection
    @Enumerated(EnumType.STRING)
    @CollectionTable(name = "notification_channels")
    private Set<ChannelType> channels = new HashSet<>();

    @Column(name = "scheduled_at")
    private LocalDateTime scheduledAt;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    @Column(name = "read_at")
    private LocalDateTime readAt;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "template_id")
    private String templateId;

    @ElementCollection
    @MapKeyColumn(name = "param_key")
    @Column(name = "param_value")
    @CollectionTable(name = "notification_parameters")
    private Map<String, String> parameters = new HashMap<>();

    @Column(name = "external_id")
    private String externalId;

    @Column(columnDefinition = "TEXT")
    private String metadata;

    @PrePersist
    void prePersist() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (status == null) {
            status = NotificationStatus.PENDING;
        }
        if (priority == null) {
            priority = NotificationPriority.MEDIUM;
        }
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
