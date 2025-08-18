package module.notification.entities;

import jakarta.persistence.*;
import module.notification.enums.ChannelType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "notification_channels_config")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationChannel {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel_type", nullable = false)
    private ChannelType channelType;

    @Column(name = "is_enabled")
    private Boolean isEnabled = true;

    @Column(name = "max_retry_count")
    private Integer maxRetryCount = 3;

    @Column(name = "retry_delay_minutes")
    private Integer retryDelayMinutes = 5;

    @Column(name = "configuration", columnDefinition = "TEXT")
    private String configuration;
}
