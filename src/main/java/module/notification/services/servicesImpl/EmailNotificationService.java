package module.notification.services.servicesImpl;

import io.micrometer.common.util.StringUtils;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import module.notification.config.NotificationProperties;
import module.notification.entities.Notification;
import module.notification.enums.ChannelType;
import module.notification.exceptions.NotificationException;
import module.notification.services.Iservices.NotificationChannelService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;

import javax.naming.Context;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "notification.email.enabled", havingValue = "true")
@Slf4j
public class EmailNotificationService implements NotificationChannelService {

    private final JavaMailSender mailSender;
    private final NotificationProperties properties;
    private final TemplateEngine templateEngine;

    @Override
    public ChannelType getChannelType() {
        return ChannelType.EMAIL;
    }

    @Override
    public void send(Notification notification) throws MessagingException {
        if (StringUtils.isBlank(notification.getRecipientEmail())) {
            throw new NotificationException("Email du destinataire non fourni");
        }

        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

        helper.setFrom(properties.getEmail().getFrom());
        helper.setTo(notification.getRecipientEmail());
        helper.setSubject(notification.getTitle());

        String content = processTemplate(notification);
        helper.setText(content, true);

        mailSender.send(message);
        log.info("Email envoyé à {}", notification.getRecipientEmail());
    }

    @Override
    public int getRetryCount() {
        return NotificationChannelService.super.getRetryCount();
    }

    @Override
    public int getRetryDelayMinutes() {
        return NotificationChannelService.super.getRetryDelayMinutes();
    }

    private String processTemplate(Notification notification) {
        if (StringUtils.isNotBlank(notification.getTemplateId())) {
            Context context = new Context();
            context.setVariables(notification.getParameters());
            return templateEngine.process("email/" + notification.getTemplateId(), context);
        }
        return notification.getContent();
    }
}
