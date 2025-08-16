package module.notification.providers;

public interface SmsProvider {
    void sendSms(String phoneNumber, String message) throws Exception;
    boolean isConfigured();
}
