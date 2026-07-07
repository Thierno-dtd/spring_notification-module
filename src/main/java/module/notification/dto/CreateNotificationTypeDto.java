package module.notification.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateNotificationTypeDto {

    @NotBlank
    @Schema(example = "PROMO_RAMADAN", description = "Code unique, sera normalisé en majuscules")
    private String code;

    @NotBlank
    @Schema(example = "Promo Ramadan")
    private String label;

    @Schema(example = "Offre spéciale envoyée pendant le Ramadan")
    private String description;
}