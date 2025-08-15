package module.notification.services.servicesImpl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import module.notification.config.NotificationProperties;
import module.notification.entities.Notification;
import module.notification.enums.ChannelType;
import module.notification.exceptions.NotificationException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "notification.sms.enabled", havingValue = "true")
@Slf4j
public class SmsNotificationService implements NotificationChannelService {
    private final NotificationProperties properties;
    private final NotificationTemplateService templateService;

    @Override
    public void send(Notification notification) throws Exception {
        if (!StringUtils.hasText(notification.getRecipientPhone())) {
            throw new NotificationException("Numéro de téléphone du destinataire non fourni");
        }

        String message = processMessage(notification);
        sendSms(notification.getRecipientPhone(), message);

        log.info("SMS envoyé à {}", notification.getRecipientPhone());
    }

    @Override
    public boolean isEnabled() {
        return properties.getSms().isEnabled();
    }

    private String processMessage(Notification notification) {
        if (StringUtils.hasText(notification.getTemplateId())) {
            try {
                var template = templateService.getTemplate(notification.getTemplateId());
                if (StringUtils.hasText(template.getSmsTemplate())) {
                    return templateService.processTemplate(template.getSmsTemplate(), notification.getParameters());
                }
            } catch (Exception e) {
                log.warn("Erreur lors du traitement du template SMS: {}", e.getMessage());
            }
        }
        return notification.getContent();
    }

    private void sendSms(String phoneNumber, String message) throws Exception {
        // Implémentation selon le provider configuré
        String provider = properties.getSms().getProvider();

        switch (provider.toLowerCase()) {
            case "twilio":
                sendViaTwilio(phoneNumber, message);
                break;
            case "aws-sns":
                sendViaAwsSns(phoneNumber, message);
                break;
            default:
                throw new NotificationException("Provider SMS non supporté: " + provider);
        }
    }

    private void sendViaTwilio(String phoneNumber, String message) throws Exception {
        // Implémentation Twilio
        // Twilio.init(properties.getSms().getApiKey(), properties.getSms().getApiSecret());
        // Message.creator(new PhoneNumber(phoneNumber), new PhoneNumber(properties.getSms().getFromNumber()), message).create();
        log.info("SMS envoyé via Twilio à {}", phoneNumber);
    }

    private void sendViaAwsSns(String phoneNumber, String message) throws Exception {
        // Implémentation AWS SNS
        log.info("SMS envoyé via AWS SNS à {}", phoneNumber);
    }
}
