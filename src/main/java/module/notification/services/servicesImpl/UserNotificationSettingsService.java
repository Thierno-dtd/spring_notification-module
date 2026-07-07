package module.notification.services.servicesImpl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import module.notification.enums.ChannelType;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserNotificationSettingsService {

    // Gestion des préférences utilisateur pour les notifications
    public boolean isChannelEnabledForUser(String userId, ChannelType channel, String type) {
        // Vérifier les préférences utilisateur
        return true; // Implémentation par défaut
    }

    public void updateUserChannelSetting(String userId, ChannelType channel, String type, boolean enabled) {
        // Mettre à jour les préférences utilisateur
    }
}
