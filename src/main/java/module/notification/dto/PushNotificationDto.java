package module.notification.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PushNotificationDto {

    @NotBlank(message = "Le titre est requis")
    private String title;

    private String body;

    @NotBlank(message = "L'ID du destinataire est requis")
    private String recipientId;

    private String pushToken;

    private String imageUrl;

    private String clickAction;

    private String sound;

    private String badge;

    private String category;

    private Boolean contentAvailable;

    private Boolean mutableContent;

    private String threadId;

    private Map<String, String> additionalData = new HashMap<>();

    // Configuration spécifique Android
    private AndroidConfig androidConfig;

    // Configuration spécifique iOS
    private IosConfig iosConfig;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AndroidConfig {
        private String icon;
        private String color;
        private String tag;
        private String channelId;
        private Integer priority; // -2 à 2
        private String[] vibratePattern;
        private Boolean autoCancel;
        private String largeIcon;
        private String bigText;
        private String bigPicture;
        private Map<String, String> customData;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class IosConfig {
        private String subtitle;
        private Integer badge;
        private String sound;
        private Boolean contentAvailable;
        private Boolean mutableContent;
        private String category;
        private String threadId;
        private String targetContentId;
        private Double interruptionLevel; // 0.0 à 1.0
        private Map<String, String> customPayload;
    }

    // Méthodes utilitaires
    public void addData(String key, String value) {
        if (this.additionalData == null) {
            this.additionalData = new HashMap<>();
        }
        this.additionalData.put(key, value);
    }

    public void removeData(String key) {
        if (this.additionalData != null) {
            this.additionalData.remove(key);
        }
    }

    public String getData(String key) {
        return this.additionalData != null ? this.additionalData.get(key) : null;
    }

    public boolean hasData(String key) {
        return this.additionalData != null && this.additionalData.containsKey(key);
    }

    // Validation personnalisée
    public boolean isValid() {
        return title != null && !title.trim().isEmpty() &&
                recipientId != null && !recipientId.trim().isEmpty();
    }

    // Création rapide pour des cas simples
    public static PushNotificationDto createSimple(String title, String body, String recipientId) {
        return PushNotificationDto.builder()
                .title(title)
                .body(body)
                .recipientId(recipientId)
                .build();
    }

    public static PushNotificationDto createWithToken(String title, String body, String recipientId, String pushToken) {
        return PushNotificationDto.builder()
                .title(title)
                .body(body)
                .recipientId(recipientId)
                .pushToken(pushToken)
                .build();
    }

    // Configuration par défaut pour Android
    public void setDefaultAndroidConfig() {
        this.androidConfig = AndroidConfig.builder()
                .priority(1) // Priorité normale
                .autoCancel(true)
                .build();
    }

    // Configuration par défaut pour iOS
    public void setDefaultIosConfig() {
        this.iosConfig = IosConfig.builder()
                .badge(1)
                .sound("default")
                .contentAvailable(true)
                .build();
    }
}