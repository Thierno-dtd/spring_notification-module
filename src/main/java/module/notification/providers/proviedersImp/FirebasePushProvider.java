package module.notification.providers.proviedersImp;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import module.notification.config.NotificationProperties;
import module.notification.exceptions.NotificationException;
import module.notification.providers.Iproviders.PushNotificationProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Map;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "notification.push.provider", havingValue = "firebase")
@Slf4j
public class FirebasePushProvider implements PushNotificationProvider {

    private final NotificationProperties properties;
    private FirebaseMessaging firebaseMessaging;
    private boolean initialized = false;

    @PostConstruct
    public void initialize() {
        try {
            if (isConfigured()) {
                initializeFirebase();
                initialized = true;
                log.info("Firebase Push Provider initialisé avec succès pour le projet: {}",
                        properties.getPush().getFirebase().getProjectId());
            } else {
                log.warn("Firebase Push Provider non configuré - vérifiez les propriétés");
            }
        } catch (Exception e) {
            log.error("Erreur lors de l'initialisation de Firebase", e);
            initialized = false;
        }
    }

    private void initializeFirebase() throws IOException {
        // Vérifier si Firebase est déjà initialisé
        if (!FirebaseApp.getApps().isEmpty()) {
            firebaseMessaging = FirebaseMessaging.getInstance();
            return;
        }

        String credentialsJson = properties.getPush().getFirebase().getCredentialsJson();

        GoogleCredentials credentials;
        if (StringUtils.hasText(credentialsJson)) {
            // Utiliser les credentials JSON directement
            credentials = GoogleCredentials.fromStream(
                    new ByteArrayInputStream(credentialsJson.getBytes())
            );
        } else if (StringUtils.hasText(properties.getPush().getFirebase().getCredentialsPath())) {
            // Utiliser le chemin vers le fichier JSON
            try (var stream = getClass().getClassLoader()
                    .getResourceAsStream(properties.getPush().getFirebase().getCredentialsPath())) {
                credentials = GoogleCredentials.fromStream(stream);
            }
        } else {
            // Utiliser les credentials par défaut (variable d'environnement GOOGLE_APPLICATION_CREDENTIALS)
            credentials = GoogleCredentials.getApplicationDefault();
        }

        FirebaseOptions options = FirebaseOptions.builder()
                .setCredentials(credentials)
                .setProjectId(properties.getPush().getFirebase().getProjectId())
                .build();

        FirebaseApp.initializeApp(options);
        firebaseMessaging = FirebaseMessaging.getInstance();
    }

    @Override
    public void sendPushNotification(String token, String title, String body, Map<String, String> additionalData) throws Exception {
        if (!initialized) {
            throw new NotificationException("Firebase Push Provider non initialisé");
        }

        if (!validateToken(token)) {
            throw new NotificationException("Token Firebase invalide");
        }

        try {
            Message.Builder messageBuilder = Message.builder()
                    .setToken(token)
                    .setNotification(Notification.builder()
                            .setTitle(title)
                            .setBody(body)
                            .build());

            // Ajouter les données personnalisées
            if (additionalData != null && !additionalData.isEmpty()) {
                messageBuilder.putAllData(additionalData);
            }

            // Configuration spécifique Android/iOS selon les besoins
            messageBuilder.setAndroidConfig(AndroidConfig.builder()
                    .setPriority(AndroidConfig.Priority.HIGH)
                    .setNotification(AndroidNotification.builder()
                            .setClickAction("FLUTTER_NOTIFICATION_CLICK")
                            .build())
                    .build());

            messageBuilder.setApnsConfig(ApnsConfig.builder()
                    .setAps(Aps.builder()
                            .setAlert(ApsAlert.builder()
                                    .setTitle(title)
                                    .setBody(body)
                                    .build())
                            .build())
                    .build());

            Message message = messageBuilder.build();
            String response = firebaseMessaging.send(message);

            log.info("Notification push envoyée avec succès. Response: {}", response);

        } catch (FirebaseMessagingException e) {
            log.error("Erreur Firebase lors de l'envoi push au token {}: {}", maskToken(token), e.getMessage());
            throw new NotificationException("Erreur Firebase: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("Erreur générale lors de l'envoi push: {}", e.getMessage());
            throw new NotificationException("Erreur lors de l'envoi push", e);
        }
    }

    @Override
    public void sendPushNotificationToTopic(String topic, String title, String body, Map<String, String> additionalData) throws Exception {
        if (!initialized) {
            throw new NotificationException("Firebase Push Provider non initialisé");
        }

        if (!StringUtils.hasText(topic)) {
            throw new NotificationException("Topic requis pour l'envoi au topic");
        }

        try {
            Message.Builder messageBuilder = Message.builder()
                    .setTopic(topic)
                    .setNotification(Notification.builder()
                            .setTitle(title)
                            .setBody(body)
                            .build());

            // Ajouter les données personnalisées
            if (additionalData != null && !additionalData.isEmpty()) {
                messageBuilder.putAllData(additionalData);
            }

            Message message = messageBuilder.build();
            String response = firebaseMessaging.send(message);

            log.info("Notification push envoyée au topic '{}' avec succès. Response: {}", topic, response);

        } catch (FirebaseMessagingException e) {
            log.error("Erreur Firebase lors de l'envoi au topic '{}': {}", topic, e.getMessage());
            throw new NotificationException("Erreur Firebase: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("Erreur lors de l'envoi push au topic: {}", e.getMessage());
            throw new NotificationException("Erreur lors de l'envoi push au topic", e);
        }
    }

    @Override
    public boolean isConfigured() {
        NotificationProperties.Push.Firebase firebaseConfig = properties.getPush().getFirebase();

        return StringUtils.hasText(firebaseConfig.getProjectId()) &&
                (StringUtils.hasText(firebaseConfig.getCredentialsJson()) ||
                        StringUtils.hasText(firebaseConfig.getCredentialsPath()) ||
                        System.getenv("GOOGLE_APPLICATION_CREDENTIALS") != null);
    }

    /**
     * Envoie une notification push à plusieurs tokens
     */
    public void sendMulticastNotification(java.util.List<String> tokens, String title, String body, Map<String, String> additionalData) throws Exception {
        if (!initialized) {
            throw new NotificationException("Firebase Push Provider non initialisé");
        }

        if (tokens == null || tokens.isEmpty()) {
            throw new NotificationException("Liste de tokens vide");
        }

        try {
            MulticastMessage.Builder messageBuilder = MulticastMessage.builder()
                    .addAllTokens(tokens)
                    .setNotification(Notification.builder()
                            .setTitle(title)
                            .setBody(body)
                            .build());

            if (additionalData != null && !additionalData.isEmpty()) {
                messageBuilder.putAllData(additionalData);
            }

            MulticastMessage message = messageBuilder.build();
            BatchResponse response = firebaseMessaging.sendMulticast(message);

            log.info("Notification multicast envoyée: {} succès, {} échecs",
                    response.getSuccessCount(), response.getFailureCount());

            // Log des erreurs spécifiques
            if (response.getFailureCount() > 0) {
                for (int i = 0; i < response.getResponses().size(); i++) {
                    if (!response.getResponses().get(i).isSuccessful()) {
                        log.warn("Échec pour le token {}: {}",
                                maskToken(tokens.get(i)),
                                response.getResponses().get(i).getException().getMessage());
                    }
                }
            }

        } catch (FirebaseMessagingException e) {
            log.error("Erreur Firebase lors de l'envoi multicast: {}", e.getMessage());
            throw new NotificationException("Erreur Firebase multicast: " + e.getMessage(), e);
        }
    }

    /**
     * Subscribe des tokens à un topic
     */
    public void subscribeToTopic(java.util.List<String> tokens, String topic) throws Exception {
        if (!initialized) {
            throw new NotificationException("Firebase Push Provider non initialisé");
        }

        try {
            TopicManagementResponse response = firebaseMessaging.subscribeToTopic(tokens, topic);
            log.info("Abonnement au topic '{}': {} succès, {} échecs",
                    topic, response.getSuccessCount(), response.getFailureCount());
        } catch (FirebaseMessagingException e) {
            log.error("Erreur lors de l'abonnement au topic '{}': {}", topic, e.getMessage());
            throw new NotificationException("Erreur d'abonnement au topic: " + e.getMessage(), e);
        }
    }

    /**
     * Unsubscribe des tokens d'un topic
     */
    public void unsubscribeFromTopic(java.util.List<String> tokens, String topic) throws Exception {
        if (!initialized) {
            throw new NotificationException("Firebase Push Provider non initialisé");
        }

        try {
            TopicManagementResponse response = firebaseMessaging.unsubscribeFromTopic(tokens, topic);
            log.info("Désabonnement du topic '{}': {} succès, {} échecs",
                    topic, response.getSuccessCount(), response.getFailureCount());
        } catch (FirebaseMessagingException e) {
            log.error("Erreur lors du désabonnement du topic '{}': {}", topic, e.getMessage());
            throw new NotificationException("Erreur de désabonnement du topic: " + e.getMessage(), e);
        }
    }

    @PreDestroy
    @Override
    public void cleanup() {
        if (initialized) {
            log.info("Nettoyage Firebase Push Provider");
            // Firebase se nettoie automatiquement
        }
    }

    private String maskToken(String token) {
        if (token == null || token.length() < 10) {
            return "***";
        }
        return token.substring(0, 4) + "***" + token.substring(token.length() - 4);
    }
}