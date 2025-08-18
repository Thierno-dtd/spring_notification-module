package module.notification.services.Iservices;

import module.notification.entities.Notification;
import module.notification.enums.ChannelType;
import module.notification.enums.NotificationPriority;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

public interface NotificationChannelService {

    /**
     * Retourne le type de canal géré par ce service
     */
    ChannelType getChannelType();

    /**
     * Envoie une notification via ce canal
     * @param notification la notification à envoyer
     * @throws Exception en cas d'erreur d'envoi
     */
    void send(Notification notification) throws Exception;

    /**
     * Envoie une notification de façon asynchrone
     * @param notification la notification à envoyer
     * @return CompletableFuture représentant l'opération d'envoi
     */
    default CompletableFuture<Void> sendAsync(Notification notification) {
        return CompletableFuture.runAsync(() -> {
            try {
                send(notification);
            } catch (Exception e) {
                throw new RuntimeException("Erreur lors de l'envoi asynchrone", e);
            }
        });
    }

    /**
     * Vérifie si ce canal est activé
     * @return true si le canal est activé
     */
    boolean isEnabled();

    /**
     * Vérifie si ce canal est correctement configuré
     * @return true si la configuration est valide
     */
    default boolean isConfigured() {
        return true;
    }

    /**
     * Retourne le nombre maximum de tentatives de retry pour ce canal
     * @return nombre de tentatives
     */
    default int getMaxRetryCount() {
        return 3;
    }

    /**
     * Retourne le délai initial entre les tentatives en minutes
     * @return délai en minutes
     */
    default int getRetryDelayMinutes() {
        return 5;
    }

    /**
     * Vérifie si ce canal supporte une priorité donnée
     * @param priority la priorité à vérifier
     * @return true si la priorité est supportée
     */
    default boolean supportsPriority(NotificationPriority priority) {
        return true;
    }

    /**
     * Retourne les limites de débit pour ce canal
     * @return map des limites (ex: "per_minute" -> 10, "per_hour" -> 100)
     */
    default Map<String, Integer> getRateLimits() {
        return Map.of(
                "per_minute", 10,
                "per_hour", 100
        );
    }

    /**
     * Valide qu'une notification peut être envoyée via ce canal
     * @param notification la notification à valider
     * @throws IllegalArgumentException si la validation échoue
     */
    default void validateNotification(Notification notification) {
        if (notification == null) {
            throw new IllegalArgumentException("La notification ne peut pas être null");
        }
        if (notification.getTitle() == null || notification.getTitle().trim().isEmpty()) {
            throw new IllegalArgumentException("Le titre de la notification est requis");
        }
    }

    /**
     * Effectue des actions de nettoyage si nécessaire
     */
    default void cleanup() {
        // Implémentation par défaut vide
    }

    /**
     * Retourne des métriques sur ce canal
     * @return map des métriques
     */
    default Map<String, Object> getMetrics() {
        return Map.of(
                "channel_type", getChannelType().name(),
                "enabled", isEnabled(),
                "configured", isConfigured()
        );
    }

    /**
     * Test de connectivité/santé du canal
     * @return true si le canal est opérationnel
     */
    default boolean healthCheck() {
        return isEnabled() && isConfigured();
    }
}