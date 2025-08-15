package module.notification.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import module.notification.enums.ChannelType;
import module.notification.enums.NotificationPriority;
import module.notification.enums.NotificationType;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BulkNotificationDto {
    @NotBlank
    private String title;

    private String content;

    private NotificationType type;
    private NotificationPriority priority;

    @NotEmpty
    private Set<ChannelType> channels;

    private LocalDateTime scheduledAt;
    private String templateId;
    private Map<String, String> parameters;

    @NotEmpty
    private List<RecipientDto> recipients;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RecipientDto {
        private String id;
        private String email;
        private String phone;
        private Map<String, String> customParameters;
    }
}

