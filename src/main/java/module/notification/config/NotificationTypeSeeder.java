package module.notification.config;

import lombok.RequiredArgsConstructor;
import module.notification.entities.NotificationType;
import module.notification.repositories.NotificationTypeRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class NotificationTypeSeeder implements CommandLineRunner {

    private final NotificationTypeRepository notificationTypeRepository;

    @Override
    public void run(String... args) {
        seed("WELCOME", "Bienvenue", "Envoyé à la création d'un compte");
        seed("ALERT", "Alerte", "Notification d'alerte");
        seed("REMINDER", "Rappel", "Rappel d'une action à faire");
        seed("PROMOTION", "Promotion", "Offre ou promotion");
        seed("TRANSACTION", "Transaction", "Confirmation d'une transaction");
        seed("SECURITY", "Sécurité", "Alerte de sécurité (connexion, mot de passe, etc.)");
        seed("SYSTEM", "Système", "Notification technique interne");
        seed("CUSTOM", "Personnalisé", "Type libre défini par un administrateur");
    }

    private void seed(String code, String label, String description) {
        if (notificationTypeRepository.existsById(code)) {
            return;
        }
        notificationTypeRepository.save(
                NotificationType.builder()
                        .code(code)
                        .label(label)
                        .description(description)
                        .isActive(true)
                        .isSystemDefined(true)
                        .build()
        );
    }
}