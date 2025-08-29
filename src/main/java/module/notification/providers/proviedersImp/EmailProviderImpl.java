package module.notification.providers.proviedersImp;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import module.notification.config.NotificationProperties;
import module.notification.entities.Notification;
import module.notification.providers.Iproviders.EmailProvider;
import module.notification.services.servicesImpl.NotificationTemplateService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Map;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "notification.email.enabled", havingValue = "true")
@Slf4j
public class EmailProviderImpl implements EmailProvider {

    private final JavaMailSender mailSender;
    private final NotificationProperties properties;
    private final NotificationTemplateService templateService;

    @Override
    public void sendEmail(String to, String subject, String content) throws Exception {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

        helper.setFrom(properties.getEmail().getFrom());
        helper.setTo(to);
        helper.setSubject(subject);
        helper.setText(content, true);

        mailSender.send(message);
        log.info("Email envoyé avec succès à: {}", to);
    }

    @Override
    public void sendEmailWithTemplate(String to, String subject, String templateId, Map<String, Object> variables) throws Exception {
        // Convertir Map<String, Object> en Map<String, String>
        Map<String, String> stringParameters = null;
        if (variables != null) {
            stringParameters = variables.entrySet().stream()
                    .collect(java.util.stream.Collectors.toMap(
                            Map.Entry::getKey,
                            entry -> entry.getValue() != null ? entry.getValue().toString() : ""
                    ));
        }

        // Récupérer le template depuis la base de données via le service
        String content = templateService.processTemplateById(templateId, stringParameters);
        sendEmail(to, subject, content);
    }

    /**
     * Nouvelle méthode pour envoyer un email à partir d'une notification
     * avec template stocké en base de données
     */
    public void sendNotificationEmail(Notification notification) throws Exception {
        if (!StringUtils.hasText(notification.getRecipientEmail())) {
            throw new IllegalArgumentException("Email destinataire manquant pour la notification: " + notification.getId());
        }

        String content;

        // Si un template est spécifié, l'utiliser
        if (notification.getTemplateId() != null) {
            content = templateService.processTemplateById(notification.getTemplateId(), notification.getParameters());
        } else {
            // Sinon utiliser le contenu direct de la notification
            content = processContent(notification.getContent(), notification.getParameters());
        }

        sendEmail(notification.getRecipientEmail(), notification.getTitle(), content);
    }

    /**
     * Traite le contenu en remplaçant les variables par leurs valeurs
     */
    private String processContent(String content, Map<String, String> parameters) {
        if (content == null || parameters == null || parameters.isEmpty()) {
            return content;
        }

        String processedContent = content;
        for (Map.Entry<String, String> entry : parameters.entrySet()) {
            String placeholder = "{{" + entry.getKey() + "}}";
            if (entry.getValue() != null) {
                processedContent = processedContent.replace(placeholder, entry.getValue());
            }
        }

        return processedContent;
    }

    @Override
    public boolean isConfigured() {
        return StringUtils.hasText(properties.getEmail().getHost()) &&
                StringUtils.hasText(properties.getEmail().getFrom());
    }
}