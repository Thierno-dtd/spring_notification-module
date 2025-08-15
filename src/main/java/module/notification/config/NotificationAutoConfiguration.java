package module.notification.config;

import module.notification.services.servicesImpl.EmailNotificationService;
import module.notification.services.servicesImpl.NotificationService;
import module.notification.services.servicesImpl.PushNotificationService;
import module.notification.services.servicesImpl.SmsNotificationService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@Configuration
@EnableConfigurationProperties(NotificationProperties.class)
@ConditionalOnClass(NotificationService.class)
@EnableJpaRepositories(basePackages = "module.notification.repositories")
@EntityScan(basePackages = "module.notification.entities")
public class NotificationAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean
    public NotificationService notificationService() {
        return new NotificationService();
    }

    @Bean
    @ConditionalOnProperty(name = "notification.email.enabled", havingValue = "true")
    public EmailNotificationService emailNotificationService() {
        return new EmailNotificationService();
    }

    @Bean
    @ConditionalOnProperty(name = "notification.sms.enabled", havingValue = "true")
    public SmsNotificationService smsNotificationService() {
        return new SmsNotificationService();
    }

    @Bean
    @ConditionalOnProperty(name = "notification.push.enabled", havingValue = "true")
    public PushNotificationService pushNotificationService() {
        return new PushNotificationService();
    }
}