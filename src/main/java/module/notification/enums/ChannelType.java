package module.notification.enums;

public enum ChannelType {
    EMAIL("email"),
    SMS("sms"),
    PUSH("push"),
    WEB("web"),
    WEBSOCKET("websocket");

    private final String value;

    ChannelType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}
