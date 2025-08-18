package module.notification.enums;

public enum NotificationPriority {
    LOW(1),
    MEDIUM(2),
    HIGH(3),
    URGENT(4);

    private final int value;

    NotificationPriority(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }
}
