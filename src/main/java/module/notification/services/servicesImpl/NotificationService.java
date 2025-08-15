package module.notification.services.servicesImpl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import module.notification.dto.BulkNotificationDto;
import module.notification.dto.NotificationDto;
import module.notification.dto.NotificationRequestDto;
import module.notification.entities.Notification;
import module.notification.enums.ChannelType;
import module.notification.enums.NotificationStatus;
import module.notification.exceptions.NotificationException;
import module.notification.mappers.NotificationMapper;
import module.notification.repositories.NotificationRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;
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

    @Async
    public CompletableFuture<NotificationDto> sendNotification(NotificationRequestDto request) {
        try {
            Notification notification = createNotification(request);
            notification = notificationRepository.save(notification);

            if (notification.getScheduledAt() != null && notification.getScheduledAt().isAfter(LocalDateTime.now())) {
                scheduleNotification(notification);
            } else {
                processNotification(notification);
            }

            return CompletableFuture.completedFuture(notificationMapper.toDto(notification));
        } catch (Exception e) {
            log.error("Erreur lors de l'envoi de notification", e);
            throw new NotificationException("Erreur lors de l'envoi de notification", e);
        }
    }

    @Async
    public CompletableFuture<List<NotificationDto>> sendBulkNotifications(BulkNotificationDto bulkRequest) {
        List<CompletableFuture<NotificationDto>> futures = bulkRequest.getRecipients()
                .stream()
                .map(recipient -> {
                    NotificationRequestDto request = NotificationRequestDto.builder()
                            .title(bulkRequest.getTitle())
                            .content(bulkRequest.getContent())
                            .type(bulkRequest.getType())
                            .priority(bulkRequest.getPriority())
                            .channels(bulkRequest.getChannels())
                            .templateId(bulkRequest.getTemplateId())
                            .parameters(bulkRequest.getParameters())
                            .recipientId(recipient.getId())
                            .recipientEmail(recipient.getEmail())
                            .recipientPhone(recipient.getPhone())
                            .build();
                    return sendNotification(request);
                })
                .collect(Collectors.toList());

        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenApply(v -> futures.stream()
                        .map(CompletableFuture::join)
                        .collect(Collectors.toList()));
    }

    private void processNotification(Notification notification) {
        notification.setStatus(NotificationStatus.PROCESSING);
        notificationRepository.save(notification);

        for (ChannelType channel : notification.getChannels()) {
            NotificationChannelService channelService = getChannelService(channel);
            if (channelService != null) {
                try {
                    channelService.send(notification);
                } catch (Exception e) {
                    log.error("Erreur lors de l'envoi via le canal {}", channel, e);
                }
            }
        }

        notification.setStatus(NotificationStatus.SENT);
        notification.setSentAt(LocalDateTime.now());
        notificationRepository.save(notification);

        // Publier un événement
        eventPublisher.publishEvent(new NotificationSentEvent(notification));
    }

    public Page<NotificationDto> getNotificationsByRecipient(String recipientId, Pageable pageable) {
        Page<Notification> notifications = notificationRepository.findByRecipientIdOrderByCreatedAtDesc(recipientId, pageable);
        return notifications.map(notificationMapper::toDto);
    }

    public void markAsRead(Long notificationId, String recipientId) {
        Notification notification = notificationRepository.findByIdAndRecipientId(notificationId, recipientId)
                .orElseThrow(() -> new NotificationException("Notification non trouvée"));

        notification.setReadAt(LocalDateTime.now());
        notification.setStatus(NotificationStatus.READ);
        notificationRepository.save(notification);

        eventPublisher.publishEvent(new NotificationReadEvent(notification));
    }

    public Long getUnreadCount(String recipientId) {
        return notificationRepository.countByRecipientIdAndReadAtIsNull(recipientId);
    }

    @Scheduled(fixedDelay = 60000) // Vérifier chaque minute
    public void processScheduledNotifications() {
        List<Notification> scheduledNotifications = notificationRepository
                .findByStatusAndScheduledAtBefore(NotificationStatus.SCHEDULED, LocalDateTime.now());

        scheduledNotifications.forEach(this::processNotification);
    }
}
