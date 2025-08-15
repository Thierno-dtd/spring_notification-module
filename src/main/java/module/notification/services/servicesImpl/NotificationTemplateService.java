package module.notification.services.servicesImpl;

import module.notification.dto.NotificationTemplateDto;
import module.notification.entities.NotificationTemplate;
import module.notification.enums.NotificationType;
import module.notification.exceptions.TemplateNotFoundException;
import module.notification.mappers.NotificationMapper;
import module.notification.repositories.NotificationTemplateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationTemplateService {

    private final NotificationTemplateRepository templateRepository;
    private final NotificationMapper notificationMapper;

    private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\{\\{(\\w+)\\}\\}");

    @Transactional
    public NotificationTemplateDto createTemplate(NotificationTemplateDto templateDto) {
        // Extraire les variables du template
        templateDto.setVariables(extractVariables(
                templateDto.getEmailTemplate(),
                templateDto.getSmsTemplate(),
                templateDto.getPushTemplate(),
                templateDto.getWebTemplate()
        ));

        NotificationTemplate template = notificationMapper.toEntity(templateDto);
        template = templateRepository.save(template);

        log.info("Template créé: {}", template.getId());
        return notificationMapper.toDto(template);
    }

    public NotificationTemplateDto getTemplate(String templateId) {
        NotificationTemplate template = templateRepository.findByIdAndIsActiveTrue(templateId)
                .orElseThrow(() -> new TemplateNotFoundException("Template non trouvé: " + templateId));

        return notificationMapper.toDto(template);
    }

    public List<NotificationTemplateDto> getTemplatesByType(NotificationType type) {
        return templateRepository.findByTypeAndIsActiveTrue(type)
                .stream()
                .map(notificationMapper::toDto)
                .collect(Collectors.toList());
    }

    public List<NotificationTemplateDto> getAllActiveTemplates() {
        return templateRepository.findByIsActiveTrue()
                .stream()
                .map(notificationMapper::toDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public NotificationTemplateDto updateTemplate(String templateId, NotificationTemplateDto templateDto) {
        NotificationTemplate existingTemplate = templateRepository.findById(templateId)
                .orElseThrow(() -> new TemplateNotFoundException("Template non trouvé: " + templateId));

        // Mettre à jour les champs
        existingTemplate.setName(templateDto.getName());
        existingTemplate.setDescription(templateDto.getDescription());
        existingTemplate.setType(templateDto.getType());
        existingTemplate.setEmailSubject(templateDto.getEmailSubject());
        existingTemplate.setEmailTemplate(templateDto.getEmailTemplate());
        existingTemplate.setSmsTemplate(templateDto.getSmsTemplate());
        existingTemplate.setPushTemplate(templateDto.getPushTemplate());
        existingTemplate.setWebTemplate(templateDto.getWebTemplate());
        existingTemplate.setIsActive(templateDto.getIsActive());

        // Re-extraire les variables
        existingTemplate.setVariables(extractVariables(
                templateDto.getEmailTemplate(),
                templateDto.getSmsTemplate(),
                templateDto.getPushTemplate(),
                templateDto.getWebTemplate()
        ));

        existingTemplate = templateRepository.save(existingTemplate);

        log.info("Template mis à jour: {}", templateId);
        return notificationMapper.toDto(existingTemplate);
    }

    @Transactional
    public void deleteTemplate(String templateId) {
        NotificationTemplate template = templateRepository.findById(templateId)
                .orElseThrow(() -> new TemplateNotFoundException("Template non trouvé: " + templateId));

        template.setIsActive(false);
        templateRepository.save(template);

        log.info("Template désactivé: {}", templateId);
    }

    public String processTemplate(String template, Map<String, String> parameters) {
        if (template == null || parameters == null) {
            return template;
        }

        String processedTemplate = template;
        for (Map.Entry<String, String> param : parameters.entrySet()) {
            String placeholder = "{{" + param.getKey() + "}}";
            processedTemplate = processedTemplate.replace(placeholder, param.getValue());
        }

        return processedTemplate;
    }

    private Set<String> extractVariables(String... templates) {
        Set<String> variables = new HashSet<>();

        for (String template : templates) {
            if (template != null) {
                Matcher matcher = VARIABLE_PATTERN.matcher(template);
                while (matcher.find()) {
                    variables.add(matcher.group(1));
                }
            }
        }

        return variables;
    }
}
