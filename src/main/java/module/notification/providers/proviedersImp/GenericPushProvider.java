package module.notification.providers.proviedersImp;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import module.notification.config.NotificationProperties;
import module.notification.exceptions.NotificationException;
import module.notification.providers.Iproviders.PushNotificationProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import jakarta.annotation.PostConstruct;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "notification.push.provider", havingValue = "generic", matchIfMissing = true)
@Slf4j
public class GenericPushProvider implements PushNotificationProvider {

    private final NotificationProperties properties;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate = new RestTemplate();

    @PostConstruct
    public void initialize() {
        if (isConfigured()) {
            log.info("Generic Push Provider initialisé avec succès. Server URL: {}",
                    properties.getPush().getServerUrl());
        } else {
            log.warn("Generic Push Provider non configuré - vérifiez les propriétés");
        }
    }

    @Override
    public void sendPushNotification(String token, String title, String body, Map<String, String> additionalData) throws Exception {
        if (!isConfigured()) {
            throw new NotificationException("Generic Push Provider non configuré");
        }

        if (!validateToken(token)) {
            throw new NotificationException("Token push invalide");
        }

        try {
            String url = properties.getPush().getServerUrl();

            Map<String, Object> payload = createPayload(token, title, body, additionalData);
            HttpHeaders headers = createHeaders();

            HttpEntity<String> request = new HttpEntity<>(
                    objectMapper.writeValueAsString(payload),
                    headers
            );

            ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("Push notification envoyée avec succès via Generic Provider au token: {}",
                        maskToken(token));
            } else {
                throw new NotificationException("Erreur HTTP: " + response.getStatusCode());
            }

        } catch (Exception e) {
            log.error("Erreur lors de l'envoi push via Generic Provider: {}", e.getMessage());
            throw new NotificationException("Erreur Generic Push Provider: " + e.getMessage(), e);
        }
    }

    @Override
    public void sendPushNotificationToTopic(String topic, String title, String body, Map<String, String> additionalData) throws Exception {
        if (!isConfigured()) {
            throw new NotificationException("Generic Push Provider non configuré");
        }

        if (!StringUtils.hasText(topic)) {
            throw new NotificationException("Topic requis");
        }

        try {
            String url = properties.getPush().getServerUrl() + "/topic";

            Map<String, Object> payload = createTopicPayload(topic, title, body, additionalData);
            HttpHeaders headers = createHeaders();

            HttpEntity<String> request = new HttpEntity<>(
                    objectMapper.writeValueAsString(payload),
                    headers
            );

            ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("Push notification envoyée au topic '{}' avec succès via Generic Provider", topic);
            } else {
                throw new NotificationException("Erreur HTTP: " + response.getStatusCode());
            }

        } catch (Exception e) {
            log.error("Erreur lors de l'envoi push au topic via Generic Provider: {}", e.getMessage());
            throw new NotificationException("Erreur Generic Push Provider topic: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean isConfigured() {
        return StringUtils.hasText(properties.getPush().getServerUrl()) &&
                StringUtils.hasText(properties.getPush().getApiKey());
    }

    private Map<String, Object> createPayload(String token, String title, String body, Map<String, String> additionalData) {
        Map<String, Object> payload = new HashMap<>();

        // Structure générique de payload
        payload.put("to", token);

        Map<String, Object> notification = new HashMap<>();
        notification.put("title", title);
        notification.put("body", body);
        payload.put("notification", notification);

        if (additionalData != null && !additionalData.isEmpty()) {
            payload.put("data", additionalData);
        }

        // Ajouter des métadonnées
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("timestamp", System.currentTimeMillis());
        metadata.put("provider", "generic");
        payload.put("metadata", metadata);

        return payload;
    }

    private Map<String, Object> createTopicPayload(String topic, String title, String body, Map<String, String> additionalData) {
        Map<String, Object> payload = new HashMap<>();

        payload.put("topic", topic);

        Map<String, Object> notification = new HashMap<>();
        notification.put("title", title);
        notification.put("body", body);
        payload.put("notification", notification);

        if (additionalData != null && !additionalData.isEmpty()) {
            payload.put("data", additionalData);
        }

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("timestamp", System.currentTimeMillis());
        metadata.put("provider", "generic");
        metadata.put("type", "topic");
        payload.put("metadata", metadata);

        return payload;
    }

    private HttpHeaders createHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        if (StringUtils.hasText(properties.getPush().getApiKey())) {
            headers.setBearerAuth(properties.getPush().getApiKey());
        }

        // Ajouter des headers personnalisés si nécessaire
        headers.set("User-Agent", "NotificationModule/1.0");

        return headers;
    }

    private String maskToken(String token) {
        if (token == null || token.length() < 10) {
            return "***";
        }
        return token.substring(0, 4) + "***" + token.substring(token.length() - 4);
    }

    /**
     * Méthode utilitaire pour tester la connectivité
     */
    public boolean testConnection() {
        try {
            if (!isConfigured()) {
                return false;
            }

            String testUrl = properties.getPush().getServerUrl() + "/health";
            HttpHeaders headers = createHeaders();
            HttpEntity<Void> request = new HttpEntity<>(headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    testUrl, HttpMethod.GET, request, String.class);

            return response.getStatusCode().is2xxSuccessful();

        } catch (Exception e) {
            log.warn("Test de connexion Generic Push Provider échoué: {}", e.getMessage());
            return false;
        }
    }
}