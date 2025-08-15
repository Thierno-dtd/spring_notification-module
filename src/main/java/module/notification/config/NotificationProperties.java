package module.notification.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "notification")
@Data
public class NotificationProperties {

    private Email email = new Email();
    private Sms sms = new Sms();
    private Push push = new Push();
    private WebSocket webSocket = new WebSocket();

    @Data
    public static class Email {
        private boolean enabled = true;
        private String host;
        private int port = 587;
        private String username;
        private String password;
        private String from;
        private boolean authEnabled = true;
        private boolean tlsEnabled = true;
    }

    @Data
    public static class Sms {
        private boolean enabled = false;
        private String provider; // "twilio", "aws-sns", etc.
        private String apiKey;
        private String apiSecret;
        private String fromNumber;
    }

    @Data
    public static class Push {
        private boolean enabled = false;
        private String firebaseServerKey;
        private String apnsCertPath;
    }

    @Data
    public static class WebSocket {
        private boolean enabled = true;
        private String endpoint = "/notifications";
    }
}
