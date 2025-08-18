package module.notification.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import module.notification.enums.ChannelType;
import module.notification.enums.NotificationPriority;
import module.notification.enums.NotificationType;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class NotificationRequestDto {

    @NotBlank
    private String title;

    private String content;

    @NotNull
    private NotificationType type;

    private NotificationPriority priority = NotificationPriority.MEDIUM;

    @NotBlank
    private String recipientId;

    private String recipientEmail;

    private String recipientPhone;

    private String senderId;

    @NotEmpty
    private Set<ChannelType> channels;

    @Future
    private LocalDateTime scheduledAt;

    private String templateId;

    private Map<String, String> parameters = new HashMap<>();

    private String externalId;

    private String metadata;
}
