package module.notification.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
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

    @Schema(description = "Identifiant du template. Laisser vide à la création : il est généré automatiquement à partir du nom.", example = "")
    private String id;

    @NotBlank
    @Schema(example = "welcome_email")
    private String name;

    @Schema(example = "Email de bienvenue envoyé à l'inscription")
    private String description;

    private NotificationType type;

    @Schema(example = "Bienvenue {{name}} !")
    private String emailSubject;

    @Schema(description = "Utiliser {{variable}} (double accolades) pour les variables dynamiques.",
            example = "Bonjour {{name}}, bienvenue chez {{company}} !")
    private String emailTemplate;

    @Schema(example = "Bonjour {{name}}, votre code est {{code}}.")
    private String smsTemplate;

    @Schema(example = "Nouvelle notification pour {{name}}")
    private String pushTemplate;

    @Schema(example = "Bonjour {{name}}")
    private String webTemplate;

    @Schema(description = "Optionnel : variables explicites en plus de celles détectées automatiquement dans les templates ({{...}}). "
            + "Ce champ est fusionné avec la détection automatique, il n'a pas besoin d'être exhaustif.",
            example = "[\"name\", \"company\"]")
    private Set<String> variables;

    private Boolean isActive;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
