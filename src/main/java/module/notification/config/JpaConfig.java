package module.notification.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@Configuration
@EnableJpaAuditing
@ConditionalOnProperty(name = "notification.jpa.enabled", havingValue = "true", matchIfMissing = true)
public class JpaConfig {
}