package module.notification.providers.Iproviders;

public interface SmsProvider {
    void sendSms(String phoneNumber, String message) throws Exception;
    boolean isConfigured();
}
