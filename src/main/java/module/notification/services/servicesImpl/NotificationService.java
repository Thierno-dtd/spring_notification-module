package module.notification.services.servicesImpl;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import module.notification.dto.BulkNotificationDto;
import module.notification.dto.NotificationDto;
import module.notification.dto.NotificationRequestDto;
import module.notification.entities.Notification;
import module.notification.enums.ChannelType;
import module.notification.enums.NotificationStatus;
import module.notification.events.NotificationFailedEvent;
import module.notification.events.NotificationReadEvent;
import module.notification.events.NotificationSentEvent;
import module.notification.exceptions.NotificationException;
import module.notification.mappers.NotificationMapper;
import module.notification.repositories.NotificationRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
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

    // Map des services par type de canal pour un accès rapide
    private Map<ChannelType, NotificationChannelService> channelServiceMap;

    @PostConstruct
    public void initChannelServiceMap() {
        channelServiceMap = channelServices.stream()
                .collect(Collectors.toMap(
                        NotificationChannelService::getChannelType,
                        Function.identity()
                ));
    }

    @Async("notificationTaskExecutor")
    @Transactional
    public CompletableFuture<NotificationDto> sendNotification(NotificationRequestDto request) {
        try {
            // Créer la notification
            Notification notification = createNotificationFromRequest(request);
            notification = notificationRepository.save(notification);

            // Planifier ou envoyer immédiatement
            if (notification.getScheduledAt() != null && notification.getScheduledAt().isAfter(LocalDateTime.now())) {
                notification.setStatus(NotificationStatus.SCHEDULED);
                notification = notificationRepository.save(notification);
            } else {
                processNotification(notification);
            }

            return CompletableFuture.completedFuture(notificationMapper.toDto(notification));

        } catch (Exception e) {
            log.error("Erreur lors de l'envoi de notification", e);
            throw new NotificationException("Erreur lors de l'envoi de notification: " + e.getMessage(), e);
        }
    }

    @Async("notificationTaskExecutor")
    @Transactional
    public CompletableFuture<List<NotificationDto>> sendBulkNotifications(BulkNotificationDto bulkRequest) {
        List<CompletableFuture<NotificationDto>> futures = bulkRequest.getRecipients()
                .stream()
                .map(recipient -> {
                    NotificationRequestDto request = createIndividualRequest(bulkRequest, recipient);
                    return sendNotification(request);
                })
                .collect(Collectors.toList());

        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenApply(v -> futures.stream()
                        .map(CompletableFuture::join)
                        .collect(Collectors.toList()));
    }

    private void processNotification(Notification notification) {
        try {
            notification.setStatus(NotificationStatus.PROCESSING);
            notification = notificationRepository.save(notification);

            boolean anySuccess = false;

            // Envoyer via chaque canal configuré
            for (ChannelType channel : notification.getChannels()) {
                try {
                    NotificationChannelService channelService = channelServiceMap.get(channel);

                    if (channelService != null && channelService.isEnabled()) {
                        channelService.send(notification);
                        anySuccess = true;
                        log.debug("Notification {} envoyée via {}", notification.getId(), channel);
                    } else {
                        log.warn("Service de canal {} non disponible ou désactivé", channel);
                    }

                } catch (Exception e) {
                    log.error("Erreur lors de l'envoi via le canal {} pour la notification {}",
                            channel, notification.getId(), e);

                    // Publier événement d'échec pour ce canal
                    eventPublisher.publishEvent(new NotificationFailedEvent(this, notification,
                            "Échec canal " + channel + ": " + e.getMessage(), e));
                }
            }

            // Mettre à jour le statut final
            if (anySuccess) {
                notification.setStatus(NotificationStatus.SENT);
                notification.setSentAt(LocalDateTime.now());
                eventPublisher.publishEvent(new NotificationSentEvent(this, notification));
            } else {
                notification.setStatus(NotificationStatus.FAILED);
            }

            notificationRepository.save(notification);

        } catch (Exception e) {
            log.error("Erreur fatale lors du traitement de la notification {}", notification.getId(), e);
            notification.setStatus(NotificationStatus.FAILED);
            notificationRepository.save(notification);

            eventPublisher.publishEvent(new NotificationFailedEvent(this, notification,
                    "Erreur fatale: " + e.getMessage(), e));
        }
    }

    @Transactional(readOnly = true)
    public Page<NotificationDto> getNotificationsByRecipient(String recipientId, Pageable pageable) {
        Page<Notification> notifications = notificationRepository
                .findByRecipientIdOrderByCreatedAtDesc(recipientId, pageable);
        return notifications.map(notificationMapper::toDto);
    }

    @Transactional(readOnly = true)
    public List<NotificationDto> getUnreadNotifications(String recipientId) {
        List<Notification> notifications = notificationRepository.findUnreadNotifications(recipientId);
        return notifications.stream()
                .map(notificationMapper::toDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public void markAsRead(Long notificationId, String recipientId) {
        Notification notification = notificationRepository
                .findByIdAndRecipientId(notificationId, recipientId)
                .orElseThrow(() -> new NotificationException("Notification non trouvée"));

        if (notification.getReadAt() == null) {
            notification.setReadAt(LocalDateTime.now());
            notification.setStatus(NotificationStatus.READ);
            notificationRepository.save(notification);

            eventPublisher.publishEvent(new NotificationReadEvent(this, notification));
        }
    }

    @Transactional
    public void markAllAsRead(String recipientId) {
        List<Notification> unreadNotifications = notificationRepository.findUnreadNotifications(recipientId);
        LocalDateTime now = LocalDateTime.now();

        unreadNotifications.forEach(notification -> {
            notification.setReadAt(now);
            notification.setStatus(NotificationStatus.READ);
        });

        notificationRepository.saveAll(unreadNotifications);

        log.info("Marqué {} notifications comme lues pour {}", unreadNotifications.size(), recipientId);
    }

    @Transactional(readOnly = true)
    public Long getUnreadCount(String recipientId) {
        return notificationRepository.countByRecipientIdAndReadAtIsNull(recipientId);
    }

    @Scheduled(fixedDelay = 60000) // Vérifier chaque minute
    @Transactional
    public void processScheduledNotifications() {
        List<Notification> scheduledNotifications = notificationRepository
                .findByStatusAndScheduledAtBefore(NotificationStatus.SCHEDULED, LocalDateTime.now());

        log.debug("Traitement de {} notifications programmées", scheduledNotifications.size());

        scheduledNotifications.forEach(this::processNotification);
    }

    @Scheduled(fixedDelay = 300000) // Nettoyage toutes les 5 minutes
    @Transactional
    public void cleanupOldNotifications() {
        // Supprimer les notifications anciennes (ex: plus de 6 mois)
        LocalDateTime cutoffDate = LocalDateTime.now().minusMonths(6);

        // Cette implémentation peut être ajustée selon vos besoins de rétention
        // notificationRepository.deleteByCreatedAtBefore(cutoffDate);
    }

    // Méthodes utilitaires privées

    private Notification createNotificationFromRequest(NotificationRequestDto request) {
        Notification notification = Notification.builder()
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

        return notification;
    }

    private NotificationRequestDto createIndividualRequest(BulkNotificationDto bulk, BulkNotificationDto.RecipientDto recipient) {
        // Fusionner les paramètres globaux avec les paramètres spécifiques au destinataire
        Map<String, String> finalParameters = new java.util.HashMap<>(bulk.getParameters());
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
}