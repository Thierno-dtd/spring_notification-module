package module.notification.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import module.notification.enums.ChannelType;
import module.notification.enums.NotificationPriority;
import module.notification.enums.NotificationStatus;
import module.notification.enums.NotificationType;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class NotificationDto {
    private Long id;
    private String title;
    private String content;
    private NotificationType type;
    private NotificationPriority priority;
    private NotificationStatus status;
    private String recipientId;
    private String recipientEmail;
    private String recipientPhone;
    private String senderId;
    private Set<ChannelType> channels;
    private LocalDateTime scheduledAt;
    private LocalDateTime sentAt;
    private LocalDateTime readAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String templateId;
    private Map<String, String> parameters;
    private String externalId;
    private String metadata;
    private boolean isRead;
}
