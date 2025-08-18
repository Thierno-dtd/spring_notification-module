package module.notification.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;

@ConfigurationProperties(prefix = "notification")
@Data
@Validated
public class NotificationProperties {

    private boolean enabled = true;

    @Valid
    private Email email = new Email();

    @Valid
    private Sms sms = new Sms();

    @Valid
    private Push push = new Push();

    @Valid
    private WebSocket webSocket = new WebSocket();

    @Valid
    private Retry retry = new Retry();

    @Valid
    private RateLimit rateLimit = new RateLimit();

    @Valid
    private Cleanup cleanup = new Cleanup();

    @Valid
    private Cache cache = new Cache();

    @Data
    public static class Email {
        private boolean enabled = true;
        private String host;
        private int port = 587;
        private String username;
        private String password;
        private String from;
        private String fromName = "Notification Service";
        private boolean authEnabled = true;
        private boolean tlsEnabled = true;
        private boolean startTlsEnabled = true;
        private String protocol = "smtp";
        private boolean debug = false;

        // Templates configuration
        private Templates templates = new Templates();

        @Data
        public static class Templates {
            private String basePath = "classpath:/templates/email/";
            private String defaultTemplate = "default";
            private String encoding = "UTF-8";
        }
    }

    @Data
    public static class Sms {
        private boolean enabled = false;

        @NotBlank(groups = SmsValidation.class)
        private String provider; // "twilio", "aws-sns", "nexmo", etc.

        private String apiKey;
        private String apiSecret;
        private String fromNumber;
        private String region = "us-east-1"; // Pour AWS SNS

        // Configuration spécifique aux providers
        private Twilio twilio = new Twilio();
        private AwsSns awsSns = new AwsSns();
        private Nexmo nexmo = new Nexmo();

        @Data
        public static class Twilio {
            private String accountSid;
            private String authToken;
            private String messagingServiceSid;
        }

        @Data
        public static class AwsSns {
            private String accessKey;
            private String secretKey;
            private String region = "us-east-1";
        }

        @Data
        public static class Nexmo {
            private String apiKey;
            private String apiSecret;
            private String applicationId;
            private String privateKey;
        }

        public interface SmsValidation {
        }
    }

    @Data
    public static class Push {
        private boolean enabled = false;
        private String provider = "generic"; // "firebase", "apns", "onesignal", etc.
        private String apiKey;
        private String apiSecret;
        private String serverUrl;

        // Firebase Configuration
        private Firebase firebase = new Firebase();

        // Apple Push Notification Service
        private Apns apns = new Apns();

        @Data
        public static class Firebase {
            private String projectId;
            private String serviceAccountPath;
            private String serverKey;
        }

        @Data
        public static class Apns {
            private String keyId;
            private String teamId;
            private String keyPath;
            private boolean production = false;
        }
    }

    @Data
    public static class WebSocket {
        private boolean enabled = true;
        private String endpoint = "/ws/notifications";
        private String[] allowedOrigins = {"*"};
        private boolean sockJsEnabled = true;
        private String applicationDestinationPrefix = "/app";
        private String[] enableSimpleBroker = {"/topic", "/queue"};
        private String userDestinationPrefix = "/user";
    }

    @Data
    public static class Retry {
        @Min(0)
        private int maxAttempts = 3;

        @NotNull
        private Duration initialDelay = Duration.ofMinutes(5);

        @Min(1)
        private double backoffMultiplier = 2.0;

        @NotNull
        private Duration maxDelay = Duration.ofHours(2);
    }

    @Data
    public static class RateLimit {
        private int maxNotificationsPerMinute = 10;
        private int maxNotificationsPerHour = 100;
        private int maxEmailPerHour = 50;
        private int maxSmsPerHour = 20;
        private boolean enabled = true;
    }

    @Data
    public static class Cleanup {
        private boolean enabled = true;
        private Duration retentionPeriod = Duration.ofDays(30);
        private String cronExpression = "0 0 2 * * ?";
    }

    @Data
    public static class Cache {
        private boolean enabled = true;
        private Duration ttl = Duration.ofHours(1);
        private int maxSize = 1000;
    }
}