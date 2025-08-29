package module.notification.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import module.notification.mappers.NotificationMapper;
import module.notification.providers.Iproviders.SmsProvider;
import module.notification.repositories.NotificationRepository;
import module.notification.repositories.NotificationTemplateRepository;
import module.notification.services.Iservices.NotificationChannelService;
import module.notification.services.servicesImpl.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.thymeleaf.TemplateEngine;

import java.util.List;
import java.util.Properties;

@Configuration
@EnableConfigurationProperties(NotificationProperties.class)
@ConditionalOnClass(NotificationService.class)
@EnableJpaRepositories(basePackages = "module.notification.repositories")
@EntityScan(basePackages = "module.notification.entities")
public class NotificationAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public NotificationService notificationService(
            NotificationRepository notificationRepository,
            NotificationMapper notificationMapper,
            List<NotificationChannelService> channelServices,
            NotificationTemplateService templateService,
            ApplicationEventPublisher eventPublisher,
            UserNotificationSettingsService userSettingsService) {
        return new NotificationService(notificationRepository, notificationMapper, channelServices,
                templateService, eventPublisher, userSettingsService);
    }

    @Bean
    @ConditionalOnProperty(name = "notification.email.enabled", havingValue = "true")
    @ConditionalOnClass(name = "org.springframework.mail.javamail.JavaMailSender")
    public JavaMailSender javaMailSender(NotificationProperties properties) {
        JavaMailSenderImpl mailSender = new JavaMailSenderImpl();

        mailSender.setHost(properties.getEmail().getHost());
        mailSender.setPort(properties.getEmail().getPort());
        mailSender.setUsername(properties.getEmail().getUsername());
        mailSender.setPassword(properties.getEmail().getPassword());

        Properties props = mailSender.getJavaMailProperties();
        props.put("mail.transport.protocol", properties.getEmail().getProtocol());
        props.put("mail.smtp.auth", properties.getEmail().isAuthEnabled());
        props.put("mail.smtp.starttls.enable", properties.getEmail().isStartTlsEnabled());
        props.put("mail.smtp.ssl.enable", properties.getEmail().isTlsEnabled());
        props.put("mail.debug", properties.getEmail().isDebug());

        return mailSender;
    }

    @Bean
    @ConditionalOnProperty(name = "notification.email.enabled", havingValue = "true")
    @ConditionalOnClass(name = "org.springframework.mail.javamail.JavaMailSender")
    public EmailNotificationService emailNotificationService(
            JavaMailSender mailSender,
            NotificationProperties properties,
            TemplateEngine templateEngine,
            NotificationTemplateRepository templateRepository) {
        return new EmailNotificationService(mailSender, properties, templateEngine, templateRepository);
    }

    @Bean
    @ConditionalOnProperty(name = "notification.sms.enabled", havingValue = "true")
    public SmsNotificationService smsNotificationService(
            NotificationProperties properties,
            SmsProvider smsProvider,
            NotificationTemplateRepository templateRepository) {
        return new SmsNotificationService(properties, smsProvider, templateRepository);
    }

    @Bean
    @ConditionalOnProperty(name = "notification.websocket.enabled", havingValue = "true")
    public WebSocketNotificationService webSocketNotificationService(
            SimpMessagingTemplate messagingTemplate,
            ObjectMapper objectMapper) {
        return new WebSocketNotificationService(messagingTemplate, objectMapper);
    }
}