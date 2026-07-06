package module.notification.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Origines autorisées pour les appels cross-origin (CORS).
 * Alimenté par la variable d'environnement CORS_ORIGINS (voir .env.example).
 */

@ConfigurationProperties(prefix = "app.cors")
@Data

public class CorsProperties {


    private List<String> allowedOrigins = List.of("http://localhost:3000");
}

