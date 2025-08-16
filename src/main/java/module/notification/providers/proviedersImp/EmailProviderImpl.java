package module.notification.providers.proviedersImp;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import module.notification.config.NotificationProperties;
import module.notification.providers.Iproviders.EmailProvider;
import org.springframework.boot.autoconfigure.AutoConfigureOrder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.util.Map;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "notification.email.enabled", havingValue = "true")
@Slf4j
public class EmailProviderImpl implements EmailProvider {

    private final JavaMailSender mailSender;
    private final NotificationProperties properties;
    private final TemplateEngine templateEngine;

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
    public void sendEmailWithTemplate(String to, String subject, String templateName, Map<String, Object> variables) throws Exception {
        Context context = new Context();
        context.setVariables(variables);

        String content = templateEngine.process("email/" + templateName, context);
        sendEmail(to, subject, content);
    }

    @Override
    public boolean isConfigured() {
        return StringUtils.hasText(properties.getEmail().getHost()) &&
                StringUtils.hasText(properties.getEmail().getFrom());
    }
}

