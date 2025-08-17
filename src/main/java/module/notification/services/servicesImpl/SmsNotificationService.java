package module.notification.services.servicesImpl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import module.notification.config.NotificationProperties;
import module.notification.entities.Notification;
import module.notification.entities.NotificationTemplate;
import module.notification.enums.ChannelType;
import module.notification.exceptions.NotificationException;
import module.notification.repositories.NotificationTemplateRepository;
import module.notification.services.Iservices.NotificationChannelService;
import module.notification.providers.Iproviders.SmsProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import jakarta.annotation.PostConstruct;
import java.util.Map;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "notification.sms.enabled", havingValue = "true")
@Slf4j
public class SmsNotificationService implements NotificationChannelService {

    private final NotificationProperties properties;
    private final SmsProvider smsProvider;
    private final NotificationTemplateRepository templateRepository;

    private static final Pattern PHONE_PATTERN = Pattern.compile("^\\+?[1-9]\\d{1,14}$");

    @PostConstruct
    public void init() {
        if (isEnabled() && !isConfigured()) {
            log.warn("Service SMS activé mais mal configuré. Vérifiez les propriétés de configuration.");
        } else if (isEnabled()) {
            log.info("Service SMS initialisé avec succès. Provider: {}",
                    properties.getSms().getProvider());
        }
    }

    @Override
    public ChannelType getChannelType() {
        return ChannelType.SMS;
    }

    @Override
    public void send(Notification notification) throws Exception {
        validateNotification(notification);

        if (!isValidPhoneNumber(notification.getRecipientPhone())) {
            throw new NotificationException("Numéro de téléphone invalide: " + notification.getRecipientPhone());
        }

        try {
            String message = processContent(notification);

            // Vérifier la longueur du message SMS
            if (message.length() > 160) {
                log.warn("Message SMS long ({} caractères) pour la notification {}",
                        message.length(), notification.getId());
            }

            smsProvider.sendSms(notification.getRecipientPhone(), message);

            log.info("SMS envoyé avec succès au numéro {} - Longueur: {} caractères",
                    maskPhoneNumber(notification.getRecipientPhone()), message.length());

        } catch (Exception e) {
            log.error("Erreur lors de l'envoi du SMS au numéro {}: {}",
                    maskPhoneNumber(notification.getRecipientPhone()), e.getMessage());
            throw new NotificationException("Échec de l'envoi SMS", e);
        }
    }

    @Override
    public boolean isEnabled() {
        return properties.getSms().isEnabled();
    }

    @Override
    public boolean isConfigured() {
        return smsProvider != null && smsProvider.isConfigured();
    }

    @Override
    public void validateNotification(Notification notification) {
        NotificationChannelService.super.validateNotification(notification);

        if (!StringUtils.hasText(notification.getRecipientPhone())) {
            throw new IllegalArgumentException("Numéro de téléphone du destinataire requis pour l'envoi SMS");
        }
    }

    @Override
    public Map<String, Integer> getRateLimits() {
        return Map.of(
                "per_minute", properties.getRateLimit().getMaxSmsPerHour() / 60,
                "per_hour", properties.getRateLimit().getMaxSmsPerHour()
        );
    }

    @Override
    public boolean healthCheck() {
        try {
            return isConfigured() && isEnabled() && smsProvider.isConfigured();
        } catch (Exception e) {
            log.warn("Health check SMS échoué: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public Map<String, Object> getMetrics() {
        Map<String, Object> baseMetrics = NotificationChannelService.super.getMetrics();
        baseMetrics.put("sms_provider", properties.getSms().getProvider());
        baseMetrics.put("from_number", maskPhoneNumber(properties.getSms().getFromNumber()));
        return baseMetrics;
    }

    // Méthodes privées utilitaires

    private String processContent(Notification notification) {
        if (StringUtils.hasText(notification.getTemplateId())) {
            try {
                NotificationTemplate template = templateRepository
                        .findByIdAndIsActiveTrue(notification.getTemplateId())
                        .orElse(null);

                if (template != null && StringUtils.hasText(template.getSmsTemplate())) {
                    return processTemplate(template.getSmsTemplate(), notification.getParameters());
                }
            } catch (Exception e) {
                log.warn("Erreur lors du traitement du contenu SMS via template {}: {}",
                        notification.getTemplateId(), e.getMessage());
            }
        }

        // Fallback sur le contenu simple
        String content = notification.getContent();
        if (content == null) {
            content = notification.getTitle();
        }

        return content != null ? content : "";
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

    private boolean isValidPhoneNumber(String phoneNumber) {
        return StringUtils.hasText(phoneNumber) && PHONE_PATTERN.matcher(phoneNumber).matches();
    }

    private String maskPhoneNumber(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.length() < 6) {
            return "***";
        }
        return phoneNumber.substring(0, 3) + "***" + phoneNumber.substring(phoneNumber.length() - 3);
    }
}