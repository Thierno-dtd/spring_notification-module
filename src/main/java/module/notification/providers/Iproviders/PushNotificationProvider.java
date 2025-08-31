package module.notification.providers.Iproviders;

import java.util.Map;

public interface PushNotificationProvider {

    /**
     * Envoie une notification push à un token spécifique
     * @param token Token du device de destination
     * @param title Titre de la notification
     * @param body Corps de la notification
     * @param additionalData Données supplémentaires
     * @throws Exception en cas d'erreur d'envoi
     */
    void sendPushNotification(String token, String title, String body, Map<String, String> additionalData) throws Exception;

    /**
     * Envoie une notification push à un topic/groupe
     * @param topic Nom du topic
     * @param title Titre de la notification
     * @param body Corps de la notification
     * @param additionalData Données supplémentaires
     * @throws Exception en cas d'erreur d'envoi
     */
    void sendPushNotificationToTopic(String topic, String title, String body, Map<String, String> additionalData) throws Exception;

    /**
     * Vérifie si le provider est correctement configuré
     * @return true si configuré
     */
    boolean isConfigured();

    /**
     * Valide un token de device
     * @param token Token à valider
     * @return true si le token est valide
     */
    default boolean validateToken(String token) {
        return token != null && !token.trim().isEmpty() && token.length() > 10;
    }

    /**
     * Nettoie les ressources si nécessaire
     */
    default void cleanup() {
        // Implémentation par défaut vide
    }
}