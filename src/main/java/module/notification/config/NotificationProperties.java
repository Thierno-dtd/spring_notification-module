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
    private Web web = new Web();

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
        private String provider = "twilio"; // "twilio", "aws-sns", "nexmo", etc.

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
        private String provider = "firebase"; // "firebase", "apns", "onesignal", "generic", etc.
        private String apiKey;
        private String apiSecret;
        private String serverUrl;

        // Firebase Configuration - CORRIGÉ
        private Firebase firebase = new Firebase();

        // Apple Push Notification Service
        private Apns apns = new Apns();

        // OneSignal Configuration - AJOUTÉ
        private OneSignal oneSignal = new OneSignal();

        @Data
        public static class Firebase {
            private String projectId;
            private String credentialsPath; // Chemin vers le fichier JSON
            private String credentialsJson; // Contenu JSON direct
            private String serverKey; // Legacy server key (deprecated)
            private String databaseUrl; // Optional Firebase Realtime Database URL
        }

        @Data
        public static class Apns {
            private String keyId;
            private String teamId;
            private String keyPath;
            private String bundleId;
            private boolean production = false;
        }

        @Data
        public static class OneSignal {
            private String appId;
            private String apiKey;
            private String restApiKey;
        }
    }

    @Data
    public static class Web {
        private boolean enabled = true;
        private String webhookUrl;
        private String apiKey;
        private int maxCacheSize = 1000;
        private Duration cacheRetention = Duration.ofDays(7);
        private boolean webhookEnabled = false;
        private boolean webPushEnabled = false;

        // Configuration Web Push - AJOUTÉ
        private WebPush webPush = new WebPush();

        @Data
        public static class WebPush {
            private String publicKey;
            private String privateKey;
            private String subject; // mailto ou URL
            private int ttl = 86400; // Time to live en secondes (24h)
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
        private int maxSessionIdleTimeout = 300; // 5 minutes en secondes
        private int maxTextMessageBufferSize = 8192;
        private int maxBinaryMessageBufferSize = 8192;
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

        private boolean exponentialBackoff = true;
    }

    @Data
    public static class RateLimit {
        private boolean enabled = true;
        private int maxNotificationsPerMinute = 60;
        private int maxNotificationsPerHour = 1000;
        private int maxEmailPerHour = 500;
        private int maxSmsPerHour = 100;
        private int maxPushPerHour = 2000;
        private int maxWebPerHour = 5000;

        // Rate limiting par utilisateur
        private int maxNotificationsPerUserPerMinute = 10;
        private int maxNotificationsPerUserPerHour = 100;
    }

    @Data
    public static class Cleanup {
        private boolean enabled = true;
        private Duration retentionPeriod = Duration.ofDays(30);
        private String cronExpression = "0 0 2 * * ?"; // 2h du matin tous les jours
        private int batchSize = 1000; // Nombre de notifications à traiter par batch
        private boolean cleanupReadNotifications = true;
        private boolean cleanupFailedNotifications = false;
        private Duration failedNotificationRetention = Duration.ofDays(7);
    }

    @Data
    public static class Cache {
        private boolean enabled = true;
        private Duration ttl = Duration.ofHours(1);
        private int maxSize = 10000;
        private String provider = "caffeine"; // "caffeine", "redis", "hazelcast"

        // Configuration Redis si utilisé
        private Redis redis = new Redis();

        @Data
        public static class Redis {
            private String host = "localhost";
            private int port = 6379;
            private String password;
            private int database = 0;
            private Duration timeout = Duration.ofSeconds(2);
        }
    }

    // Méthodes utilitaires pour la validation
    public boolean isPushConfigured() {
        if (!push.enabled) return false;

        switch (push.provider.toLowerCase()) {
            case "firebase":
                return push.firebase.projectId != null &&
                        (push.firebase.credentialsJson != null ||
                                push.firebase.credentialsPath != null);
            case "apns":
                return push.apns.keyId != null &&
                        push.apns.teamId != null &&
                        push.apns.keyPath != null;
            case "onesignal":
                return push.oneSignal.appId != null &&
                        push.oneSignal.restApiKey != null;
            case "generic":
                return push.serverUrl != null && push.apiKey != null;
            default:
                return false;
        }
    }

    public boolean isWebConfigured() {
        return web.enabled; // Le service web fonctionne même sans configuration externe
    }

    public boolean isWebPushConfigured() {
        return web.webPushEnabled &&
                web.webPush.publicKey != null &&
                web.webPush.privateKey != null;
    }
}