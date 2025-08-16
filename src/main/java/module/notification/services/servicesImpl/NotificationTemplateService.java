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
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.HashMap;
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
        // Générer un ID si non fourni
        if (!StringUtils.hasText(templateDto.getId())) {
            templateDto.setId(generateTemplateId(templateDto.getName()));
        }

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

    @Transactional(readOnly = true)
    public NotificationTemplateDto getTemplate(String templateId) {
        NotificationTemplate template = templateRepository.findByIdAndIsActiveTrue(templateId)
                .orElseThrow(() -> new TemplateNotFoundException("Template non trouvé: " + templateId));

        return notificationMapper.toDto(template);
    }

    @Transactional(readOnly = true)
    public List<NotificationTemplateDto> getTemplatesByType(NotificationType type) {
        return templateRepository.findByTypeAndIsActiveTrue(type)
                .stream()
                .map(notificationMapper::toDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
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

    @Transactional(readOnly = true)
    public String testTemplate(String templateId, Map<String, String> testParameters) {
        NotificationTemplateDto template = getTemplate(templateId);

        if (testParameters == null) {
            testParameters = generateDefaultTestData(template.getVariables());
        }

        StringBuilder result = new StringBuilder();
        result.append("=== TEST DU TEMPLATE: ").append(template.getName()).append(" ===\n\n");

        if (StringUtils.hasText(template.getEmailTemplate())) {
            result.append("EMAIL:\n");
            result.append("Subject: ").append(processTemplate(template.getEmailSubject(), testParameters)).append("\n");
            result.append("Body: ").append(processTemplate(template.getEmailTemplate(), testParameters)).append("\n\n");
        }

        if (StringUtils.hasText(template.getSmsTemplate())) {
            result.append("SMS:\n");
            result.append(processTemplate(template.getSmsTemplate(), testParameters)).append("\n\n");
        }

        if (StringUtils.hasText(template.getPushTemplate())) {
            result.append("PUSH:\n");
            result.append(processTemplate(template.getPushTemplate(), testParameters)).append("\n\n");
        }

        if (StringUtils.hasText(template.getWebTemplate())) {
            result.append("WEB:\n");
            result.append(processTemplate(template.getWebTemplate(), testParameters)).append("\n\n");
        }

        result.append("Variables utilisées: ").append(template.getVariables());

        return result.toString();
    }

    public String processTemplate(String template, Map<String, String> parameters) {
        if (template == null || parameters == null) {
            return template;
        }

        String processedTemplate = template;
        for (Map.Entry<String, String> param : parameters.entrySet()) {
            String placeholder = "{{" + param.getKey() + "}}";
            String value = param.getValue() != null ? param.getValue() : "";
            processedTemplate = processedTemplate.replace(placeholder, value);
        }

        return processedTemplate;
    }

    public boolean validateTemplate(String templateContent, Set<String> requiredVariables) {
        if (!StringUtils.hasText(templateContent)) {
            return true; // Template vide est valide
        }

        Set<String> templateVariables = extractVariablesFromTemplate(templateContent);

        // Vérifier que toutes les variables requises sont présentes
        if (requiredVariables != null) {
            for (String required : requiredVariables) {
                if (!templateVariables.contains(required)) {
                    log.warn("Variable requise '{}' manquante dans le template", required);
                    return false;
                }
            }
        }

        return true;
    }

    @Transactional(readOnly = true)
    public List<NotificationTemplateDto> searchTemplates(String searchTerm) {
        return templateRepository.findByNameContainingIgnoreCaseAndIsActiveTrue(searchTerm)
                .stream()
                .map(notificationMapper::toDto)
                .collect(Collectors.toList());
    }

    private Set<String> extractVariables(String... templates) {
        Set<String> variables = new HashSet<>();

        for (String template : templates) {
            if (template != null) {
                variables.addAll(extractVariablesFromTemplate(template));
            }
        }

        return variables;
    }

    private Set<String> extractVariablesFromTemplate(String template) {
        Set<String> variables = new HashSet<>();
        if (template != null) {
            Matcher matcher = VARIABLE_PATTERN.matcher(template);
            while (matcher.find()) {
                variables.add(matcher.group(1));
            }
        }
        return variables;
    }

    private String generateTemplateId(String name) {
        String baseId = name.toLowerCase()
                .replaceAll("[^a-z0-9]", "_")
                .replaceAll("_+", "_")
                .replaceAll("^_|_$", "");

        // Ajouter timestamp pour éviter les collisions
        return baseId + "_" + System.currentTimeMillis();
    }

    private Map<String, String> generateDefaultTestData(Set<String> variables) {
        Map<String, String> testData = new HashMap<>();

        if (variables != null) {
            for (String variable : variables) {
                switch (variable.toLowerCase()) {
                    case "username":
                    case "nom":
                    case "name":
                        testData.put(variable, "John Doe");
                        break;
                    case "email":
                        testData.put(variable, "john.doe@example.com");
                        break;
                    case "phone":
                    case "telephone":
                        testData.put(variable, "+33123456789");
                        break;
                    case "date":
                        testData.put(variable, LocalDateTime.now().toString());
                        break;
                    case "amount":
                    case "montant":
                        testData.put(variable, "100.00");
                        break;
                    case "company":
                    case "entreprise":
                        testData.put(variable, "Acme Corp");
                        break;
                    default:
                        testData.put(variable, "[" + variable.toUpperCase() + "]");
                }
            }
        }

        return testData;
    }
}