package module.notification.services.servicesImpl;

import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import module.notification.dto.BulkNotificationDto;
import module.notification.dto.NotificationDto;
import module.notification.dto.NotificationRequestDto;
import module.notification.entities.Notification;
import module.notification.enums.ChannelType;
import module.notification.enums.NotificationStatus;
import module.notification.enums.NotificationType;
import module.notification.events.NotificationFailedEvent;
import module.notification.events.NotificationReadEvent;
import module.notification.events.NotificationSentEvent;
import module.notification.exceptions.NotificationException;
import module.notification.mappers.NotificationMapper;
import module.notification.repositories.NotificationRepository;
import module.notification.services.Iservices.NotificationChannelService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final NotificationMapper notificationMapper;
    private final List<NotificationChannelService> channelServices;
    private final NotificationTemplateService templateService;
    private final ApplicationEventPublisher eventPublisher;
    private final UserNotificationSettingsService userSettingsService;
    private final CircuitBreakerService circuitBreakerService;
    private final NotificationRetryService retryService;
    private final AdvancedRateLimiterService rateLimiterService;
    private final NotificationMetricsService metricsService;

    // Map des services par type de canal pour un accès rapide
    private Map<ChannelType, NotificationChannelService> channelServiceMap;

    // Cache des tentatives d'envoi pour éviter le spam
    private final Map<String, LocalDateTime> rateLimitCache = new ConcurrentHashMap<>();

    @PostConstruct
    public void initChannelServiceMap() {
        channelServiceMap = channelServices.stream()
                .collect(Collectors.toMap(
                        NotificationChannelService::getChannelType,
                        Function.identity()
                ));
        log.info("Services de notification initialisés: {}", channelServiceMap.keySet());
    }

    @Async("notificationTaskExecutor")
    @Transactional
    public CompletableFuture<NotificationDto> sendNotification(NotificationRequestDto request) {
        try {
            // Vérifier les limites de débit
            if (isRateLimited(request.getRecipientId(), request.getChannels().iterator().next(), request.getType())) {
                throw new NotificationException("Limite de débit dépassée pour le destinataire: " + request.getRecipientId());
            }

            // Créer la notification
            Notification notification = createNotificationFromRequest(request);

            // Filtrer les canaux selon les préférences utilisateur
            Set<ChannelType> allowedChannels = filterChannelsByUserPreferences(request.getRecipientId(), request.getChannels(), request.getType());
            notification.setChannels(allowedChannels);

            notification = notificationRepository.save(notification);

            // Planifier ou envoyer immédiatement
            if (notification.getScheduledAt() != null && notification.getScheduledAt().isAfter(LocalDateTime.now())) {
                notification.setStatus(NotificationStatus.SCHEDULED);
                notification = notificationRepository.save(notification);
                log.info("Notification {} planifiée pour {}", notification.getId(), notification.getScheduledAt());
            } else {
                processNotification(notification);
            }

            return CompletableFuture.completedFuture(notificationMapper.toDto(notification));

        } catch (Exception e) {
            log.error("Erreur lors de l'envoi de notification pour {}", request.getRecipientId(), e);
            throw new NotificationException("Erreur lors de l'envoi de notification: " + e.getMessage(), e);
        }
    }

    @Async("notificationTaskExecutor")
    @Transactional
    public CompletableFuture<List<NotificationDto>> sendBulkNotifications(BulkNotificationDto bulkRequest) {
        log.info("Démarrage de l'envoi en masse pour {} destinataires", bulkRequest.getRecipients().size());

        List<CompletableFuture<NotificationDto>> futures = bulkRequest.getRecipients()
                .stream()
                .map(recipient -> {
                    NotificationRequestDto request = createIndividualRequest(bulkRequest, recipient);
                    return sendNotification(request);
                })
                .collect(Collectors.toList());

        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenApply(v -> {
                    List<NotificationDto> results = futures.stream()
                            .map(future -> {
                                try {
                                    return future.get();
                                } catch (Exception e) {
                                    log.error("Erreur lors de l'envoi d'une notification en masse", e);
                                    return null;
                                }
                            })
                            .filter(Objects::nonNull)
                            .collect(Collectors.toList());

                    log.info("Envoi en masse terminé: {}/{} notifications envoyées",
                            results.size(), bulkRequest.getRecipients().size());
                    return results;
                });
    }

    private void processNotification(Notification notification) {
        long startTime = System.currentTimeMillis();

        try {
            notification.setStatus(NotificationStatus.PROCESSING);
            notification = notificationRepository.save(notification);

            boolean anySuccess = false;
            Map<ChannelType, Exception> channelErrors = new HashMap<>();

            for (ChannelType channel : notification.getChannels()) {
                try {
                    // Vérifier le circuit breaker
                    if (!circuitBreakerService.canSend(channel)) {
                        log.warn("Circuit breaker ouvert pour le canal {}, envoi ignoré", channel);
                        continue;
                    }

                    // Vérifier le rate limiting par canal
                    if (isRateLimited(notification.getRecipientId(), channel, notification.getType())) {
                        log.warn("Rate limit dépassé pour le canal {}", channel);
                        continue;
                    }

                    NotificationChannelService channelService = channelServiceMap.get(channel);

                    if (channelService != null && channelService.isEnabled()) {
                        channelService.send(notification);
                        anySuccess = true;

                        // Succès - notifier le circuit breaker
                        circuitBreakerService.onSuccess(channel);

                        // Enregistrer la métrique
                        long processingTime = System.currentTimeMillis() - startTime;
                        metricsService.recordNotificationSent(
                                notification.getId(),
                                channel,
                                notification.getType(),
                                notification.getPriority(),
                                notification.getRecipientId(),
                                notification.getTemplateId(),
                                processingTime,
                                getCurrentHttpRequest() // À implémenter
                        );

                        log.debug("Notification {} envoyée via {}", notification.getId(), channel);
                    } else {
                        log.warn("Service de canal {} non disponible ou désactivé", channel);
                    }

                } catch (Exception e) {
                    channelErrors.put(channel, e);
                    log.error("Erreur lors de l'envoi via le canal {} pour la notification {}",
                            channel, notification.getId(), e);

                    // Échec - notifier le circuit breaker
                    circuitBreakerService.onFailure(channel, e);

                    // Enregistrer la métrique d'échec
                    long processingTime = System.currentTimeMillis() - startTime;
                    metricsService.recordNotificationFailed(
                            notification.getId(),
                            channel,
                            notification.getType(),
                            notification.getPriority(),
                            notification.getRecipientId(),
                            notification.getTemplateId(),
                            processingTime,
                            e.getMessage(),
                            0, // retry count initial
                            getCurrentHttpRequest()
                    );

                    // Programmer un retry
                    retryService.scheduleRetry(notification, channel, e.getMessage());
                }
            }

            // Mettre à jour le statut final
            if (anySuccess) {
                notification.setStatus(NotificationStatus.SENT);
                notification.setSentAt(LocalDateTime.now());
                eventPublisher.publishEvent(new NotificationSentEvent(this, notification));
            } else {
                notification.setStatus(NotificationStatus.FAILED);
                notification.setMetadata(buildErrorMetadata(channelErrors));
            }

            notificationRepository.save(notification);

        } catch (Exception e) {
            log.error("Erreur fatale lors du traitement de la notification {}", notification.getId(), e);
            notification.setStatus(NotificationStatus.FAILED);
            notification.setMetadata("Erreur fatale: " + e.getMessage());
            notificationRepository.save(notification);

            eventPublisher.publishEvent(new NotificationFailedEvent(this, notification,
                    "Erreur fatale: " + e.getMessage(), e));
        }
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "userNotifications", key = "#recipientId + '_' + #pageable.pageNumber + '_' + #pageable.pageSize")
    public Page<NotificationDto> getNotificationsByRecipient(String recipientId, Pageable pageable) {
        Page<Notification> notifications = notificationRepository
                .findByRecipientIdOrderByCreatedAtDesc(recipientId, pageable);
        return notifications.map(notificationMapper::toDto);
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "unreadNotifications", key = "#recipientId")
    public List<NotificationDto> getUnreadNotifications(String recipientId) {
        List<Notification> notifications = notificationRepository.findUnreadNotifications(recipientId);
        return notifications.stream()
                .map(notificationMapper::toDto)
                .collect(Collectors.toList());
    }

    @Transactional
    @CacheEvict(value = {"userNotifications", "unreadNotifications"}, key = "#recipientId")
    public void markAsRead(Long notificationId, String recipientId) {
        Notification notification = notificationRepository
                .findByIdAndRecipientId(notificationId, recipientId)
                .orElseThrow(() -> new NotificationException("Notification non trouvée"));

        if (notification.getReadAt() == null) {
            notification.setReadAt(LocalDateTime.now());
            notification.setStatus(NotificationStatus.READ);
            notificationRepository.save(notification);

            // Enregistrer la métrique de lecture
            // Déterminer le canal principal (premier canal de la liste)
            ChannelType primaryChannel = notification.getChannels().iterator().next();
            metricsService.recordNotificationRead(
                    notificationId,
                    primaryChannel,
                    notification.getType(),
                    recipientId,
                    getCurrentHttpRequest()
            );

            eventPublisher.publishEvent(new NotificationReadEvent(this, notification));
        }
    }

    @Transactional
    @CacheEvict(value = {"userNotifications", "unreadNotifications"}, key = "#recipientId")
    public void markAllAsRead(String recipientId) {
        List<Notification> unreadNotifications = notificationRepository.findUnreadNotifications(recipientId);
        LocalDateTime now = LocalDateTime.now();

        unreadNotifications.forEach(notification -> {
            notification.setReadAt(now);
            notification.setStatus(NotificationStatus.READ);
        });

        notificationRepository.saveAll(unreadNotifications);
        log.info("Marqué {} notifications comme lues pour {}", unreadNotifications.size(), recipientId);

        // Enregistrer les métriques
        unreadNotifications.forEach(notification ->
        metricsService.recordNotificationRead(notification.getId(), notification.getChannels().iterator().next(),  notification.getType(), recipientId, getCurrentHttpRequest()));
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "unreadCount", key = "#recipientId")
    public Long getUnreadCount(String recipientId) {
        return notificationRepository.countByRecipientIdAndReadAtIsNull(recipientId);
    }

    @Transactional
    public void deleteNotification(Long notificationId, String recipientId) {
        Notification notification = notificationRepository
                .findByIdAndRecipientId(notificationId, recipientId)
                .orElseThrow(() -> new NotificationException("Notification non trouvée"));

        notificationRepository.delete(notification);
        log.info("Notification {} supprimée pour {}", notificationId, recipientId);
    }

    @Scheduled(fixedDelay = 60000) // Vérifier chaque minute
    @Transactional
    public void processScheduledNotifications() {
        List<Notification> scheduledNotifications = notificationRepository
                .findByStatusAndScheduledAtBefore(NotificationStatus.SCHEDULED, LocalDateTime.now());

        if (!scheduledNotifications.isEmpty()) {
            log.info("Traitement de {} notifications programmées", scheduledNotifications.size());
            scheduledNotifications.forEach(this::processNotification);
        }
    }

    @Scheduled(fixedDelay = 300000) // Nettoyage toutes les 5 minutes
    @Transactional
    public void cleanupOldNotifications() {
        // Supprimer les notifications anciennes selon la configuration
        LocalDateTime cutoffDate = LocalDateTime.now().minusMonths(6);

        // Implémentation du nettoyage selon vos besoins de rétention
        long deletedCount = notificationRepository.deleteByCreatedAtBeforeAndStatus(
                cutoffDate, NotificationStatus.READ);

        if (deletedCount > 0) {
            log.info("Nettoyage effectué: {} notifications supprimées", deletedCount);
        }

        // Nettoyer le cache de limitation de débit
        cleanupRateLimitCache();
    }

    // Méthodes utilitaires privées

    private Notification createNotificationFromRequest(NotificationRequestDto request) {
        return Notification.builder()
                .title(request.getTitle())
                .content(request.getContent())
                .type(request.getType())
                .priority(request.getPriority())
                .recipientId(request.getRecipientId())
                .recipientEmail(request.getRecipientEmail())
                .recipientPhone(request.getRecipientPhone())
                .senderId(request.getSenderId())
                .channels(request.getChannels())
                .scheduledAt(request.getScheduledAt())
                .templateId(request.getTemplateId())
                .parameters(request.getParameters())
                .externalId(request.getExternalId())
                .metadata(request.getMetadata())
                .status(NotificationStatus.PENDING)
                .build();
    }

    private NotificationRequestDto createIndividualRequest(BulkNotificationDto bulk, BulkNotificationDto.RecipientDto recipient) {
        Map<String, String> finalParameters = new HashMap<>(bulk.getParameters() != null ? bulk.getParameters() : new HashMap<>());
        if (recipient.getCustomParameters() != null) {
            finalParameters.putAll(recipient.getCustomParameters());
        }

        return NotificationRequestDto.builder()
                .title(bulk.getTitle())
                .content(bulk.getContent())
                .type(bulk.getType())
                .priority(bulk.getPriority())
                .channels(bulk.getChannels())
                .scheduledAt(bulk.getScheduledAt())
                .templateId(bulk.getTemplateId())
                .parameters(finalParameters)
                .recipientId(recipient.getId())
                .recipientEmail(recipient.getEmail())
                .recipientPhone(recipient.getPhone())
                .build();
    }

    private Set<ChannelType> filterChannelsByUserPreferences(String recipientId, Set<ChannelType> requestedChannels, module.notification.enums.NotificationType type) {
        return requestedChannels.stream()
                .filter(channel -> userSettingsService.isChannelEnabledForUser(recipientId, channel, type))
                .collect(Collectors.toSet());
    }

    private boolean isRateLimited(String recipientId, ChannelType channel, NotificationType type) {
        AdvancedRateLimiterService.RateLimitResult result =
                rateLimiterService.isAllowed(recipientId, channel, type);

        if (!result.isAllowed()) {
            log.warn("Rate limit dépassé pour l'utilisateur {} sur le canal {}: {}",
                    recipientId, channel, result.getReason());
        }

        return !result.isAllowed();
    }

    private void clearRateLimit(String recipientId) {
        rateLimitCache.remove(recipientId);
    }

    private void cleanupRateLimitCache() {
        LocalDateTime cutoff = LocalDateTime.now().minusHours(1);
        rateLimitCache.entrySet().removeIf(entry -> entry.getValue().isBefore(cutoff));
    }

    private void scheduleRetry(Notification notification, ChannelType channel, Exception error) {
        // Implémentation de la logique de retry
        log.debug("Tentative de reprogrammation pour la notification {} sur le canal {}",
                notification.getId(), channel);
    }

    private String buildErrorMetadata(Map<ChannelType, Exception> channelErrors) {
        if (channelErrors.isEmpty()) return null;

        StringBuilder metadata = new StringBuilder();
        metadata.append("Erreurs par canal: ");
        channelErrors.forEach((channel, error) ->
                metadata.append(channel).append(": ").append(error.getMessage()).append("; "));

        return metadata.toString();
    }

    /**
     * Obtient les métriques du service
     */
    public Map<String, Object> getServiceMetrics() {
        Map<String, Object> metrics = new HashMap<>();

        // Métriques temps réel
        metrics.put("realtime", metricsService.getRealtimeMetrics());

        // État des circuit breakers
        metrics.put("circuit_breakers", circuitBreakerService.getCircuitBreakerStatus());

        // Statistiques de retry
        metrics.put("retry_stats", retryService.getRetryStatistics());

        // Dashboard
        metrics.put("dashboard", metricsService.getDashboardMetrics());

        return metrics;
    }

    /**
     * Obtient l'état de santé du service
     */
    public Map<String, Object> getHealthStatus() {
        Map<String, Object> health = new HashMap<>();

        // Statut global
        health.put("status", "UP");
        health.put("timestamp", LocalDateTime.now().toString());

        // État des canaux
        Map<String, Object> channelHealth = new HashMap<>();
        for (NotificationChannelService service : channelServices) {
            try {
                Map<String, Object> channelMetrics = new HashMap<>(service.getMetrics()); // copie pour éviter ImmutableMap
                channelMetrics.put("enabled", service.isEnabled());
                channelMetrics.put("configured", service.isConfigured());
                channelMetrics.put("healthy", service.healthCheck());

                channelHealth.put(service.getChannelType().name().toLowerCase(), channelMetrics);
            } catch (Exception e) {
                // On capture les erreurs par service pour ne pas faire planter tout le healthCheck
                Map<String, Object> errorMetrics = new HashMap<>();
                errorMetrics.put("enabled", false);
                errorMetrics.put("healthy", false);
                errorMetrics.put("error", e.getMessage());
                channelHealth.put(service.getChannelType().name().toLowerCase(), errorMetrics);
            }
        }
        health.put("channels", channelHealth);

        // État des services transverses
        Map<String, Object> coreServices = new HashMap<>();
        coreServices.put("circuit_breaker_service", true);
        coreServices.put("retry_service", true);
        coreServices.put("rate_limiter_service", true);
        coreServices.put("metrics_service", true);

        health.put("core_services", coreServices);

        return health;
    }


    /**
     * Statistiques avancées pour admin
     */
    public Map<String, Object> getAdminStatistics(LocalDateTime startDate, LocalDateTime endDate) {
        Map<String, Object> stats = new HashMap<>();

        // Métriques par période
        stats.put("period_stats", metricsService.getPeriodStatistics(startDate, endDate));

        // Performance par canal
        stats.putAll(getServiceMetrics());

        return stats;
    }

    // 5. Méthode utilitaire pour obtenir la requête HTTP actuelle (à ajouter)
    private HttpServletRequest getCurrentHttpRequest() {
        try {
            return ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes()).getRequest();
        } catch (IllegalStateException e) {
            return null; // Pas dans un contexte web
        }
    }
}