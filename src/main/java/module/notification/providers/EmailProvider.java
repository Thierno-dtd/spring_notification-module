package module.notification.providers;

public interface EmailProvider {
    void sendEmail(String to, String subject, String content) throws Exception;
    void sendEmailWithTemplate(String to, String subject, String templateName, Map<String, Object> variables) throws Exception;
    boolean isConfigured();
}
