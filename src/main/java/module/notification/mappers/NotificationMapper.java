package module.notification.mappers;

import module.notification.dto.NotificationDto;
import module.notification.dto.NotificationTemplateDto;
import module.notification.entities.Notification;
import module.notification.entities.NotificationTemplate;
import org.springframework.stereotype.Component;

@Component
public class NotificationMapper {
    public NotificationDto toDto(Notification notification) {
        if (notification == null) return null;

        return NotificationDto.builder()
                .id(notification.getId())
                .title(notification.getTitle())
                .content(notification.getContent())
                .type(notification.getType())
                .priority(notification.getPriority())
                .status(notification.getStatus())
                .recipientId(notification.getRecipientId())
                .recipientEmail(notification.getRecipientEmail())
                .recipientPhone(notification.getRecipientPhone())
                .senderId(notification.getSenderId())
                .channels(notification.getChannels())
                .scheduledAt(notification.getScheduledAt())
                .sentAt(notification.getSentAt())
                .readAt(notification.getReadAt())
                .createdAt(notification.getCreatedAt())
                .updatedAt(notification.getUpdatedAt())
                .templateId(notification.getTemplateId())
                .parameters(notification.getParameters())
                .externalId(notification.getExternalId())
                .metadata(notification.getMetadata())
                .isRead(notification.getReadAt() != null)
                .build();
    }

    public Notification toEntity(NotificationDto dto) {
        if (dto == null) return null;

        return Notification.builder()
                .id(dto.getId())
                .title(dto.getTitle())
                .content(dto.getContent())
                .type(dto.getType())
                .priority(dto.getPriority())
                .status(dto.getStatus())
                .recipientId(dto.getRecipientId())
                .recipientEmail(dto.getRecipientEmail())
                .recipientPhone(dto.getRecipientPhone())
                .senderId(dto.getSenderId())
                .channels(dto.getChannels())
                .scheduledAt(dto.getScheduledAt())
                .sentAt(dto.getSentAt())
                .readAt(dto.getReadAt())
                .templateId(dto.getTemplateId())
                .parameters(dto.getParameters())
                .externalId(dto.getExternalId())
                .metadata(dto.getMetadata())
                .build();
    }

    public NotificationTemplateDto toDto(NotificationTemplate template) {
        if (template == null) return null;

        return NotificationTemplateDto.builder()
                .id(template.getId())
                .name(template.getName())
                .description(template.getDescription())
                .type(template.getType())
                .emailSubject(template.getEmailSubject())
                .emailTemplate(template.getEmailTemplate())
                .smsTemplate(template.getSmsTemplate())
                .pushTemplate(template.getPushTemplate())
                .webTemplate(template.getWebTemplate())
                .variables(template.getVariables())
                .isActive(template.getIsActive())
                .createdAt(template.getCreatedAt())
                .updatedAt(template.getUpdatedAt())
                .build();
    }

    public NotificationTemplate toEntity(NotificationTemplateDto dto) {
        if (dto == null) return null;

        return NotificationTemplate.builder()
                .id(dto.getId())
                .name(dto.getName())
                .description(dto.getDescription())
                .type(dto.getType())
                .emailSubject(dto.getEmailSubject())
                .emailTemplate(dto.getEmailTemplate())
                .smsTemplate(dto.getSmsTemplate())
                .pushTemplate(dto.getPushTemplate())
                .webTemplate(dto.getWebTemplate())
                .variables(dto.getVariables())
                .isActive(dto.getIsActive())
                .build();
    }
}
