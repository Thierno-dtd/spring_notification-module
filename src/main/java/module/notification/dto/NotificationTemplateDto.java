package module.notification.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import module.notification.enums.NotificationType;

import java.time.LocalDateTime;
import java.util.Set;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class NotificationTemplateDto {
    @NotBlank
    private String id;

    @NotBlank
    private String name;

    private String description;
    private NotificationType type;
    private String emailSubject;
    private String emailTemplate;
    private String smsTemplate;
    private String pushTemplate;
    private String webTemplate;
    private Set<String> variables;
    private Boolean isActive;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
