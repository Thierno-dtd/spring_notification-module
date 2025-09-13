package module.notification.services.servicesImpl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import module.notification.config.NotificationProperties;
import module.notification.entities.Notification;
import module.notification.entities.NotificationRetry;
import module.notification.enums.ChannelType;
import module.notification.repositories.NotificationRepository;
import module.notification.repositories.NotificationRetryRepository;
import module.notification.services.Iservices.NotificationChannelService;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationRetryService {

    private final NotificationRetryRepository retryRepository;
    private final NotificationRepository notificationRepository;
    private final NotificationProperties properties;
    private final List<NotificationChannelService> channelServices;
    private final CircuitBreakerService circuitBreakerService;

    private Map<ChannelType, NotificationChannelService> channelServiceMap;

    @javax.annotation.PostConstruct
    public void initChannelServiceMap() {
        channelServiceMap = channelServices.stream()
                .collect(Collectors.toMap(
                        NotificationChannelService::getChannelType,
                        Function.identity()
                ));
    }

    @Transactional
    public void scheduleRetry(Notification notification, ChannelType failedChannel, String errorMessage) {
        // Vérifier si on n'a pas déjà dépassé le nombre max de tentatives
        int currentAttempts = retryRepository.countByNotificationIdAndChannel(notification.getId(), failedChannel);

        if (currentAttempts >= properties.getRetry().getMaxAttempts()) {
            log.warn("Nombre maximum de tentatives atteint pour la notification {} sur le canal {}",
                    notification.getId(), failedChannel);
            return;
        }

        // Calculer le délai avec backoff exponentiel
        long delayMinutes = calculateDelay(currentAttempts);
        LocalDateTime nextRetry = LocalDateTime.now().plusMinutes(delayMinutes);

        NotificationRetry retry = NotificationRetry.builder()
                .notificationId(notification.getId())
                .channel(failedChannel)
                .attemptCount(currentAttempts + 1)
                .scheduledAt(nextRetry)
                .errorMessage(errorMessage)
                .status("SCHEDULED")
                .build();

        retryRepository.save(retry);

        log.info("Retry programmé pour la notification {} sur le canal {} dans {} minutes",
                notification.getId(), failedChannel, delayMinutes);
    }

    @Scheduled(fixedDelay = 120000) // Toutes les 2 minutes
    @Transactional
    public void processScheduledRetries() {
        LocalDateTime now = LocalDateTime.now();
        List<NotificationRetry> pendingRetries = retryRepository
                .findByStatusAndScheduledAtBefore("SCHEDULED", now);

        if (!pendingRetries.isEmpty()) {
            log.info("Traitement de {} tentatives de retry", pendingRetries.size());

            for (NotificationRetry retry : pendingRetries) {
                processRetry(retry);
            }
        }
    }

    @Async("notificationTaskExecutor")
    @Transactional
    public void processRetry(NotificationRetry retry) {
        try {
            // Vérifier le circuit breaker
            if (!circuitBreakerService.canSend(retry.getChannel())) {
                log.info("Circuit breaker ouvert pour le canal {}, retry reporté", retry.getChannel());
                postponeRetry(retry, 30); // Reporter de 30 minutes
                return;
            }

            // Récupérer la notification originale
            Notification notification = notificationRepository.findById(retry.getNotificationId())
                    .orElse(null);

            if (notification == null) {
                log.error("Notification {} introuvable pour le retry", retry.getNotificationId());
                retry.setStatus("FAILED");
                retry.setErrorMessage("Notification originale introuvable");
                retryRepository.save(retry);
                return;
            }

            // Marquer le retry comme en cours
            retry.setStatus("PROCESSING");
            retry.setProcessedAt(LocalDateTime.now());
            retryRepository.save(retry);

            // Tenter l'envoi
            NotificationChannelService channelService = channelServiceMap.get(retry.getChannel());
            if (channelService != null && channelService.isEnabled()) {
                channelService.send(notification);

                // Succès
                retry.setStatus("SUCCESS");
                retry.setCompletedAt(LocalDateTime.now());
                retryRepository.save(retry);

                circuitBreakerService.onSuccess(retry.getChannel());

                log.info("Retry réussi pour la notification {} sur le canal {} (tentative {})",
                        notification.getId(), retry.getChannel(), retry.getAttemptCount());

            } else {
                throw new Exception("Service de canal indisponible: " + retry.getChannel());
            }

        } catch (Exception e) {
            handleRetryFailure(retry, e);
        }
    }

    private void handleRetryFailure(NotificationRetry retry, Exception error) {
        circuitBreakerService.onFailure(retry.getChannel(), error);

        retry.setStatus("FAILED");
        retry.setErrorMessage(error.getMessage());
        retry.setCompletedAt(LocalDateTime.now());
        retryRepository.save(retry);

        log.error("Échec du retry pour la notification {} sur le canal {} (tentative {}): {}",
                retry.getNotificationId(), retry.getChannel(), retry.getAttemptCount(), error.getMessage());

        // Programmer un nouveau retry si on n'a pas atteint la limite
        if (retry.getAttemptCount() < properties.getRetry().getMaxAttempts()) {
            Notification notification = notificationRepository.findById(retry.getNotificationId()).orElse(null);
            if (notification != null) {
                scheduleRetry(notification, retry.getChannel(), error.getMessage());
            }
        } else {
            log.warn("Abandon définitif pour la notification {} sur le canal {} après {} tentatives",
                    retry.getNotificationId(), retry.getChannel(), retry.getAttemptCount());
        }
    }

    private void postponeRetry(NotificationRetry retry, int delayMinutes) {
        retry.setScheduledAt(LocalDateTime.now().plusMinutes(delayMinutes));
        retryRepository.save(retry);
    }

    private long calculateDelay(int attemptCount) {
        long baseDelay = properties.getRetry().getInitialDelay().toMinutes();

        if (properties.getRetry().isExponentialBackoff()) {
            // Backoff exponentiel: delay * (multiplier ^ attempt)
            double multiplier = properties.getRetry().getBackoffMultiplier();
            long delay = (long) (baseDelay * Math.pow(multiplier, attemptCount));

            // Limiter au délai maximum
            long maxDelay = properties.getRetry().getMaxDelay().toMinutes();
            return Math.min(delay, maxDelay);
        } else {
            // Délai fixe
            return baseDelay;
        }
    }

    public List<NotificationRetry> getRetryHistory(Long notificationId) {
        return retryRepository.findByNotificationIdOrderByCreatedAtDesc(notificationId);
    }

    public Map<String, Object> getRetryStatistics() {
        return Map.of(
                "totalRetries", retryRepository.count(),
                "successfulRetries", retryRepository.countByStatus("SUCCESS"),
                "failedRetries", retryRepository.countByStatus("FAILED"),
                "pendingRetries", retryRepository.countByStatus("SCHEDULED")
        );
    }

    @Transactional
    @Scheduled(cron = "0 0 3 * * ?") // 3h du matin tous les jours
    public void cleanupOldRetries() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(30);
        int deleted = retryRepository.deleteByCreatedAtBefore(cutoff);
        log.info("Nettoyage des anciens retries: {} entrées supprimées", deleted);
    }
}