package module.notification.services.servicesImpl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import module.notification.dto.NotificationTemplateDto;
import module.notification.enums.ChannelType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class TemplateValidationService {

    private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\{\\{(\\w+)\\}\\}");
    private static final Pattern HTML_TAG_PATTERN = Pattern.compile("<[^>]+>");
    private static final Pattern SCRIPT_PATTERN = Pattern.compile("<script[^>]*>.*?</script>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    // Longueurs maximales recommandées par canal
    private static final Map<ChannelType, Integer> MAX_LENGTHS = Map.of(
            ChannelType.EMAIL, 10000,
            ChannelType.SMS, 160,
            ChannelType.PUSH, 100,
            ChannelType.WEB, 5000,
            ChannelType.WEBSOCKET, 1000
    );

    // Variables requises selon le type de notification
    private static final Map<String, Set<String>> REQUIRED_VARIABLES_BY_TYPE = Map.of(
            "WELCOME", Set.of("username", "email"),
            "TRANSACTION", Set.of("amount", "transaction_id"),
            "SECURITY", Set.of("username", "ip_address"),
            "PROMOTION", Set.of("offer_name", "discount"),
            "REMINDER", Set.of("event_name", "date"),
            "ALERT", Set.of("message", "level")
    );

    /**
     * Valide complètement un template
     */
    public ValidationResult validateTemplate(NotificationTemplateDto template) {
        ValidationResult.ValidationResultBuilder resultBuilder = ValidationResult.builder()
                .valid(true)
                .templateId(template.getId())
                .warnings(new ArrayList<>())
                .errors(new ArrayList<>());

        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        // Validation de base
        validateBasicFields(template, errors);

        // Validation des templates par canal
        validateChannelTemplates(template, errors, warnings);

        // Validation de la sécurité
        validateSecurity(template, errors, warnings);

        // Validation des variables
        validateVariables(template, errors, warnings);

        // Validation de la cohérence
        validateConsistency(template, errors, warnings);

        resultBuilder.errors(errors)
                .warnings(warnings)
                .valid(errors.isEmpty());

        ValidationResult result = resultBuilder.build();

        if (!result.isValid()) {
            log.warn("Validation échouée pour le template {}: {}", template.getId(), errors);
        }

        return result;
    }

    /**
     * Valide un template pour un canal spécifique
     */
    public ValidationResult validateForChannel(String templateContent, ChannelType channel) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        if (!StringUtils.hasText(templateContent)) {
            return ValidationResult.builder()
                    .valid(true)
                    .errors(Collections.emptyList())
                    .warnings(Collections.singletonList("Template vide pour le canal " + channel))
                    .build();
        }

        // Validation de la longueur
        validateLength(templateContent, channel, warnings, errors);

        // Validation spécifique au canal
        switch (channel) {
            case EMAIL:
                validateEmailTemplate(templateContent, errors, warnings);
                break;
            case SMS:
                validateSmsTemplate(templateContent, errors, warnings);
                break;
            case PUSH:
                validatePushTemplate(templateContent, errors, warnings);
                break;
            case WEB:
                validateWebTemplate(templateContent, errors, warnings);
                break;
            case WEBSOCKET:
                validateWebSocketTemplate(templateContent, errors, warnings);
                break;
        }

        return ValidationResult.builder()
                .valid(errors.isEmpty())
                .errors(errors)
                .warnings(warnings)
                .build();
    }

    /**
     * Test d'un template avec des données
     */
    public ValidationResult testTemplate(NotificationTemplateDto template, Map<String, String> testData) {
        ValidationResult basicValidation = validateTemplate(template);
        if (!basicValidation.isValid()) {
            return basicValidation;
        }

        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        try {
            // Tester chaque canal
            testChannelTemplate(template.getEmailTemplate(), testData, ChannelType.EMAIL, errors, warnings);
            testChannelTemplate(template.getSmsTemplate(), testData, ChannelType.SMS, errors, warnings);
            testChannelTemplate(template.getPushTemplate(), testData, ChannelType.PUSH, errors, warnings);
            testChannelTemplate(template.getWebTemplate(), testData, ChannelType.WEB, errors, warnings);

        } catch (Exception e) {
            errors.add("Erreur lors du test du template: " + e.getMessage());
        }

        return ValidationResult.builder()
                .valid(errors.isEmpty())
                .errors(errors)
                .warnings(warnings)
                .testData(testData)
                .build();
    }

    private void validateBasicFields(NotificationTemplateDto template, List<String> errors) {
        if (!StringUtils.hasText(template.getId())) {
            errors.add("L'ID du template est requis");
        }

        if (!StringUtils.hasText(template.getName())) {
            errors.add("Le nom du template est requis");
        }

        if (template.getType() == null) {
            errors.add("Le type de notification est requis");
        }

        // Vérifier qu'au moins un template de canal existe
        boolean hasChannelTemplate = StringUtils.hasText(template.getEmailTemplate()) ||
                StringUtils.hasText(template.getSmsTemplate()) ||
                StringUtils.hasText(template.getPushTemplate()) ||
                StringUtils.hasText(template.getWebTemplate());

        if (!hasChannelTemplate) {
            errors.add("Au moins un template de canal doit être défini");
        }
    }

    private void validateChannelTemplates(NotificationTemplateDto template, List<String> errors, List<String> warnings) {
        if (StringUtils.hasText(template.getEmailTemplate())) {
            ValidationResult emailValidation = validateForChannel(template.getEmailTemplate(), ChannelType.EMAIL);
            errors.addAll(emailValidation.getErrors());
            warnings.addAll(emailValidation.getWarnings());
        }

        if (StringUtils.hasText(template.getSmsTemplate())) {
            ValidationResult smsValidation = validateForChannel(template.getSmsTemplate(), ChannelType.SMS);
            errors.addAll(smsValidation.getErrors());
            warnings.addAll(smsValidation.getWarnings());
        }

        if (StringUtils.hasText(template.getPushTemplate())) {
            ValidationResult pushValidation = validateForChannel(template.getPushTemplate(), ChannelType.PUSH);
            errors.addAll(pushValidation.getErrors());
            warnings.addAll(pushValidation.getWarnings());
        }

        if (StringUtils.hasText(template.getWebTemplate())) {
            ValidationResult webValidation = validateForChannel(template.getWebTemplate(), ChannelType.WEB);
            errors.addAll(webValidation.getErrors());
            warnings.addAll(webValidation.getWarnings());
        }
    }

    private void validateSecurity(NotificationTemplateDto template, List<String> errors, List<String> warnings) {
        // Vérifier les templates HTML pour des scripts malveillants
        checkForScripts(template.getEmailTemplate(), "Email", errors);
        checkForScripts(template.getWebTemplate(), "Web", errors);

        // Vérifier les injections potentielles
        checkForInjections(template.getEmailTemplate(), "Email", warnings);
        checkForInjections(template.getWebTemplate(), "Web", warnings);
    }

    private void validateVariables(NotificationTemplateDto template, List<String> errors, List<String> warnings) {
        Set<String> allVariables = extractAllVariables(template);

        if (template.getVariables() == null) {
            template.setVariables(allVariables);
        } else {
            // Vérifier la cohérence entre les variables déclarées et utilisées
            Set<String> declaredVars = new HashSet<>(template.getVariables());
            Set<String> usedVars = allVariables;

            // Variables déclarées mais non utilisées
            Set<String> unusedVars = new HashSet<>(declaredVars);
            unusedVars.removeAll(usedVars);
            if (!unusedVars.isEmpty()) {
                warnings.add("Variables déclarées mais non utilisées: " + unusedVars);
            }

            // Variables utilisées mais non déclarées
            Set<String> undeclaredVars = new HashSet<>(usedVars);
            undeclaredVars.removeAll(declaredVars);
            if (!undeclaredVars.isEmpty()) {
                warnings.add("Variables utilisées mais non déclarées: " + undeclaredVars);
            }
        }

        // Vérifier les variables requises selon le type
        if (template.getType() != null) {
            Set<String> requiredVars = REQUIRED_VARIABLES_BY_TYPE.get(template.getType());
            if (requiredVars != null) {
                Set<String> missingVars = new HashSet<>(requiredVars);
                missingVars.removeAll(allVariables);
                if (!missingVars.isEmpty()) {
                    warnings.add("Variables recommandées manquantes pour le type " + template.getType() + ": " + missingVars);
                }
            }
        }
    }

    private void validateConsistency(NotificationTemplateDto template, List<String> errors, List<String> warnings) {
        // Vérifier que les templates des différents canaux sont cohérents
        Set<String> emailVars = extractVariablesFromTemplate(template.getEmailTemplate());
        Set<String> smsVars = extractVariablesFromTemplate(template.getSmsTemplate());
        Set<String> pushVars = extractVariablesFromTemplate(template.getPushTemplate());
        Set<String> webVars = extractVariablesFromTemplate(template.getWebTemplate());

        // Comparer la consistance des variables entre canaux
        if (!emailVars.isEmpty() && !smsVars.isEmpty()) {
            Set<String> commonVars = new HashSet<>(emailVars);
            commonVars.retainAll(smsVars);
            if (commonVars.size() < Math.min(emailVars.size(), smsVars.size()) * 0.5) {
                warnings.add("Peu de variables communes entre email et SMS");
            }
        }
    }

    private void validateEmailTemplate(String template, List<String> errors, List<String> warnings) {
        // Vérifier la structure HTML basique
        if (template.toLowerCase().contains("html") && !template.contains("</html>")) {
            warnings.add("Template HTML email potentiellement mal formé");
        }

        // Vérifier les balises de base pour l'email
        if (!template.toLowerCase().contains("body") && template.toLowerCase().contains("html")) {
            warnings.add("Balise <body> manquante dans le template HTML");
        }
    }

    private void validateSmsTemplate(String template, List<String> errors, List<String> warnings) {
        // Vérification spécifique SMS
        if (template.contains("http://") || template.contains("https://")) {
            int urlCount = countOccurrences(template, "http://") + countOccurrences(template, "https://");
            if (urlCount > 1) {
                warnings.add("Plusieurs URLs dans un SMS peuvent affecter la délivrance");
            }
        }

        // Caractères spéciaux qui peuvent causer des problèmes
        if (template.contains("€") || template.contains("£") || template.contains("¥")) {
            warnings.add("Caractères de devise peuvent compter double dans les SMS");
        }
    }

    private void validatePushTemplate(String template, List<String> errors, List<String> warnings) {
        // Push notifications ont des limites strictes
        if (template.length() > 50) {
            warnings.add("Titre de push notification long, peut être tronqué sur certains appareils");
        }

        // Éviter les caractères spéciaux
        if (template.contains("\n")) {
            warnings.add("Retours à la ligne dans les push notifications peuvent ne pas s'afficher correctement");
        }
    }

    private void validateWebTemplate(String template, List<String> errors, List<String> warnings) {
        // Validation web similaire à email mais plus permissive
        if (template.length() > MAX_LENGTHS.get(ChannelType.WEB)) {
            warnings.add("Template web très long, peut affecter les performances");
        }
    }

    private void validateWebSocketTemplate(String template, List<String> errors, List<String> warnings) {
        // WebSocket doit souvent être en JSON
        if (template.trim().startsWith("{") && !template.trim().endsWith("}")) {
            warnings.add("Template WebSocket semble être du JSON mal formé");
        }
    }

    private void validateLength(String template, ChannelType channel, List<String> warnings, List<String> errors) {
        int maxLength = MAX_LENGTHS.getOrDefault(channel, 1000);

        if (template.length() > maxLength) {
            if (channel == ChannelType.SMS) {
                errors.add("Template SMS trop long (" + template.length() + " caractères, max " + maxLength + ")");
            } else {
                warnings.add("Template " + channel + " long (" + template.length() + " caractères, recommandé < " + maxLength + ")");
            }
        }
    }

    private void checkForScripts(String template, String channelName, List<String> errors) {
        if (template != null && SCRIPT_PATTERN.matcher(template).find()) {
            errors.add("Scripts JavaScript détectés dans le template " + channelName + " (interdit pour la sécurité)");
        }
    }

    private void checkForInjections(String template, String channelName, List<String> warnings) {
        if (template != null) {
            if (template.toLowerCase().contains("javascript:") ||
                    template.toLowerCase().contains("onclick=") ||
                    template.toLowerCase().contains("onerror=")) {
                warnings.add("Potentielles injections XSS détectées dans le template " + channelName);
            }
        }
    }

    private void testChannelTemplate(String template, Map<String, String> testData,
                                     ChannelType channel, List<String> errors, List<String> warnings) {
        if (template == null) return;

        try {
            String processed = processTemplate(template, testData);

            // Vérifier qu'aucune variable n'est restée non remplacée
            Set<String> remainingVars = extractVariablesFromTemplate(processed);
            if (!remainingVars.isEmpty()) {
                warnings.add("Variables non remplacées dans " + channel + ": " + remainingVars);
            }

            // Validation post-traitement
            ValidationResult channelValidation = validateForChannel(processed, channel);
            errors.addAll(channelValidation.getErrors());
            warnings.addAll(channelValidation.getWarnings());

        } catch (Exception e) {
            errors.add("Erreur lors du test du template " + channel + ": " + e.getMessage());
        }
    }

    private Set<String> extractAllVariables(NotificationTemplateDto template) {
        Set<String> variables = new HashSet<>();
        variables.addAll(extractVariablesFromTemplate(template.getEmailTemplate()));
        variables.addAll(extractVariablesFromTemplate(template.getSmsTemplate()));
        variables.addAll(extractVariablesFromTemplate(template.getPushTemplate()));
        variables.addAll(extractVariablesFromTemplate(template.getWebTemplate()));
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

    private int countOccurrences(String text, String substring) {
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(substring, index)) != -1) {
            count++;
            index += substring.length();
        }
        return count;
    }

    // Classes utilitaires
    @lombok.Builder
    @lombok.Data
    public static class ValidationResult {
        private boolean valid;
        private String templateId;
        private List<String> errors;
        private List<String> warnings;
        private Map<String, String> testData;

        public boolean hasWarnings() {
            return warnings != null && !warnings.isEmpty();
        }

        public boolean hasErrors() {
            return errors != null && !errors.isEmpty();
        }
    }
}