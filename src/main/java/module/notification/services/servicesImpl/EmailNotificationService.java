package module.notification.services.servicesImpl;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import module.notification.config.NotificationProperties;
import module.notification.entities.Notification;
import module.notification.entities.NotificationTemplate;
import module.notification.enums.ChannelType;
import module.notification.enums.NotificationPriority;
import module.notification.exceptions.NotificationException;
import module.notification.repositories.NotificationTemplateRepository;
import module.notification.services.Iservices.NotificationChannelService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import jakarta.annotation.PostConstruct;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "notification.email.enabled", havingValue = "true")
@Slf4j
public class EmailNotificationService implements NotificationChannelService {

    private final JavaMailSender mailSender;
    private final NotificationProperties properties;
    private final TemplateEngine templateEngine;
    private final NotificationTemplateRepository templateRepository;

    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[A-Za-z0-9+_.-]+@([A-Za-z0-9.-]+\\.[A-Za-z]{2,})$"
    );

    /*
    @Bean
    @ConditionalOnProperty(name = "notification.email.enabled", havingValue = "true")
    @ConditionalOnMissingBean
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
    */

    @PostConstruct
    public void init() {
        if (isEnabled() && !isConfigured()) {
            log.warn("Service email activé mais mal configuré. Vérifiez les propriétés de configuration.");
        } else if (isEnabled()) {
            log.info("Service email initialisé avec succès. Host: {}, Port: {}",
                    properties.getEmail().getHost(), properties.getEmail().getPort());
        }
    }

    @Override
    public ChannelType getChannelType() {
        return ChannelType.EMAIL;
    }

    @Override
    public void send(Notification notification) throws MessagingException {
        validateNotification(notification);

        if (!isValidEmail(notification.getRecipientEmail())) {
            throw new NotificationException("Email du destinataire invalide: " + notification.getRecipientEmail());
        }

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            // Configuration de base
            helper.setFrom(properties.getEmail().getFrom(), properties.getEmail().getFromName());
            helper.setTo(notification.getRecipientEmail());

            // Sujet et contenu
            String subject = processSubject(notification);
            String content = processContent(notification);

            helper.setSubject(subject);
            helper.setText(content, true); // true pour HTML

            // Configuration de la priorité
            configurePriority(message, notification.getPriority());

            // Envoi
            mailSender.send(message);

            log.info("Email envoyé avec succès à {} - Sujet: {}",
                    notification.getRecipientEmail(), subject);

        } catch (Exception e) {
            log.error("Erreur lors de l'envoi de l'email à {}: {}",
                    notification.getRecipientEmail(), e.getMessage());
            throw new NotificationException("Échec de l'envoi email", e);
        }
    }

    @Override
    public boolean isEnabled() {
        return properties.getEmail().isEnabled();
    }

    @Override
    public boolean isConfigured() {
        return StringUtils.hasText(properties.getEmail().getHost()) &&
                StringUtils.hasText(properties.getEmail().getFrom()) &&
                properties.getEmail().getPort() > 0;
    }

    @Override
    public void validateNotification(Notification notification) {
        NotificationChannelService.super.validateNotification(notification);

        if (!StringUtils.hasText(notification.getRecipientEmail())) {
            throw new IllegalArgumentException("Email du destinataire requis pour l'envoi email");
        }
    }

    @Override
    public boolean supportsPriority(NotificationPriority priority) {
        return true; // L'email supporte toutes les priorités
    }

    @Override
    public Map<String, Integer> getRateLimits() {
        return Map.of(
                "per_minute", properties.getRateLimit().getMaxEmailPerHour() / 60,
                "per_hour", properties.getRateLimit().getMaxEmailPerHour()
        );
    }

    @Override
    public boolean healthCheck() {
        try {
            // Test simple de création d'un message
            MimeMessage testMessage = mailSender.createMimeMessage();
            return testMessage != null && isConfigured() && isEnabled();
        } catch (Exception e) {
            log.warn("Health check email échoué: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public Map<String, Object> getMetrics() {
        Map<String, Object> baseMetrics = NotificationChannelService.super.getMetrics();
        baseMetrics.put("smtp_host", properties.getEmail().getHost());
        baseMetrics.put("smtp_port", properties.getEmail().getPort());
        baseMetrics.put("tls_enabled", properties.getEmail().isTlsEnabled());
        baseMetrics.put("template_base_path", properties.getEmail().getTemplates().getBasePath());
        return baseMetrics;
    }

    // Méthodes privées utilitaires

    private String processSubject(Notification notification) {
        if (StringUtils.hasText(notification.getTemplateId())) {
            try {
                NotificationTemplate template = templateRepository
                        .findByIdAndIsActiveTrue(notification.getTemplateId())
                        .orElse(null);

                if (template != null && StringUtils.hasText(template.getEmailSubject())) {
                    return processTemplate(template.getEmailSubject(), notification.getParameters());
                }
            } catch (Exception e) {
                log.warn("Erreur lors du traitement du sujet via template {}: {}",
                        notification.getTemplateId(), e.getMessage());
            }
        }

        return notification.getTitle();
    }

    private String processContent(Notification notification) {
        if (StringUtils.hasText(notification.getTemplateId())) {
            try {
                NotificationTemplate template = templateRepository
                        .findByIdAndIsActiveTrue(notification.getTemplateId())
                        .orElse(null);

                if (template != null && StringUtils.hasText(template.getEmailTemplate())) {
                    Context context = new Context();
                    if (notification.getParameters() != null) {
                        // Conversion Map<String,String> vers Map<String,Object>
                        Map<String, Object> variables = new HashMap<>(notification.getParameters());
                        context.setVariables(variables);
                    }

                    // Ajouter des variables par défaut
                    context.setVariable("title", notification.getTitle());
                    context.setVariable("content", notification.getContent());
                    context.setVariable("recipientId", notification.getRecipientId());
                    context.setVariable("type", notification.getType().getDisplayName());
                    context.setVariable("priority", notification.getPriority().name());

                    return templateEngine.process("email/" + notification.getTemplateId(), context);
                }
            } catch (Exception e) {
                log.warn("Erreur lors du traitement du contenu via template {}: {}",
                        notification.getTemplateId(), e.getMessage());
            }
        }

        // Fallback sur le contenu simple avec un wrapper HTML minimal
        return wrapContentInHtml(notification.getContent());
    }

    private String processTemplate(String template, Map<String, String> parameters) {
        if (template == null || parameters == null) {
            return template;
        }

        String processed = template;
        for (Map.Entry<String, String> param : parameters.entrySet()) {
            String placeholder = "{{" + param.getKey() + "}}";
            String value = param.getValue() != null ? param.getValue() : "";
            processed = processed.replace(placeholder, value);
        }

        return processed;
    }

    private String wrapContentInHtml(String content) {
        if (content == null) {
            return "";
        }

        // Simple wrapper HTML pour du contenu texte
        return String.format("""
            <html>
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
            </head>
            <body style="font-family: Arial, sans-serif; line-height: 1.6; color: #333;">
                <div style="max-width: 600px; margin: 0 auto; padding: 20px;">
                    %s
                </div>
            </body>
            </html>
            """, content.replace("\n", "<br>"));
    }

    private void configurePriority(MimeMessage message, NotificationPriority priority) throws MessagingException {
        if (priority != null) {
            switch (priority) {
                case URGENT:
                    message.setHeader("X-Priority", "1");
                    message.setHeader("Importance", "High");
                    break;
                case HIGH:
                    message.setHeader("X-Priority", "2");
                    message.setHeader("Importance", "High");
                    break;
                case MEDIUM:
                    message.setHeader("X-Priority", "3");
                    message.setHeader("Importance", "Normal");
                    break;
                case LOW:
                    message.setHeader("X-Priority", "5");
                    message.setHeader("Importance", "Low");
                    break;
            }
        }
    }

    private boolean isValidEmail(String email) {
        return StringUtils.hasText(email) && EMAIL_PATTERN.matcher(email).matches();
    }
}