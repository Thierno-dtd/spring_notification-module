package module.notification.enums;

public enum NotificationType {
    WELCOME("Bienvenue"),
    ALERT("Alerte"),
    REMINDER("Rappel"),
    PROMOTION("Promotion"),
    TRANSACTION("Transaction"),
    SECURITY("Sécurité"),
    SYSTEM("Système"),
    CUSTOM("Personnalisé");

    private final String displayName;

    NotificationType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
