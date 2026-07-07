package module.notification.services.servicesImpl;

import lombok.RequiredArgsConstructor;
import module.notification.entities.NotificationType;
import module.notification.repositories.NotificationTypeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class NotificationTypeService {

    private final NotificationTypeRepository notificationTypeRepository;

    public List<NotificationType> getAll() {
        return notificationTypeRepository.findAll();
    }

    public List<NotificationType> getActive() {
        return notificationTypeRepository.findByIsActiveTrue();
    }

    @Transactional
    public NotificationType create(String code, String label, String description) {
        String normalizedCode = code.trim().toUpperCase().replace(" ", "_");
        if (notificationTypeRepository.existsById(normalizedCode)) {
            throw new IllegalArgumentException("Le type '" + normalizedCode + "' existe déjà");
        }
        return notificationTypeRepository.save(
                NotificationType.builder()
                        .code(normalizedCode)
                        .label(label)
                        .description(description)
                        .isActive(true)
                        .isSystemDefined(false)
                        .build()
        );
    }

    @Transactional
    public NotificationType setActive(String code, boolean active) {
        NotificationType type = notificationTypeRepository.findById(code)
                .orElseThrow(() -> new IllegalArgumentException("Type inconnu: " + code));
        type.setIsActive(active);
        return notificationTypeRepository.save(type);
    }

    @Transactional
    public void delete(String code) {
        NotificationType type = notificationTypeRepository.findById(code)
                .orElseThrow(() -> new IllegalArgumentException("Type inconnu: " + code));
        if (Boolean.TRUE.equals(type.getIsSystemDefined())) {
            throw new IllegalStateException("Les types système ne peuvent pas être supprimés, seulement désactivés");
        }
        notificationTypeRepository.delete(type);
    }

    /** À appeler avant toute création de notification/template */
    public void validateActiveOrThrow(String code) {
        NotificationType type = notificationTypeRepository.findById(code)
                .orElseThrow(() -> new IllegalArgumentException("Type de notification inconnu: " + code));
        if (!Boolean.TRUE.equals(type.getIsActive())) {
            throw new IllegalStateException("Le type de notification '" + code + "' est désactivé");
        }
    }
}