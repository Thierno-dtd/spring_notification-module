package module.notification.providers.proviedersImp;

import com.twilio.Twilio;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import module.notification.config.NotificationProperties;
import module.notification.exceptions.NotificationException;
import module.notification.providers.Iproviders.SmsProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.PublishRequest;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "notification.sms.enabled", havingValue = "true")
@Slf4j
public class SmsProviderImpl implements SmsProvider {

    private final NotificationProperties properties;

    @Override
    public void sendSms(String phoneNumber, String message) throws Exception {
        String provider = properties.getSms().getProvider();

        switch (provider.toLowerCase()) {
            case "twilio":
                sendViaTwilio(phoneNumber, message);
                break;
            case "aws-sns":
                sendViaAwsSns(phoneNumber, message);
                break;
            default:
                throw new NotificationException("Provider SMS non supporté: " + provider);
        }
    }

    private void sendViaTwilio(String phoneNumber, String message) throws Exception {
        if (!Twilio.getAccountSid().isPresent()) {
            Twilio.init(properties.getSms().getApiKey(), properties.getSms().getApiSecret());
        }

        Message twilioMessage = Message.creator(
                new PhoneNumber(phoneNumber),
                new PhoneNumber(properties.getSms().getFromNumber()),
                message
        ).create();

        log.info("SMS envoyé via Twilio avec ID: {}", twilioMessage.getSid());
    }

    private void sendViaAwsSns(String phoneNumber, String message) throws Exception {
        AwsBasicCredentials credentials = AwsBasicCredentials.create(
                properties.getSms().getApiKey(),
                properties.getSms().getApiSecret()
        );

        try (SnsClient snsClient = SnsClient.builder()
                .region(Region.US_EAST_1)
                .credentialsProvider(StaticCredentialsProvider.create(credentials))
                .build()) {

            PublishRequest request = PublishRequest.builder()
                    .phoneNumber(phoneNumber)
                    .message(message)
                    .build();

            snsClient.publish(request);
            log.info("SMS envoyé via AWS SNS à: {}", phoneNumber);
        }
    }

    @Override
    public boolean isConfigured() {
        return StringUtils.hasText(properties.getSms().getProvider()) &&
                StringUtils.hasText(properties.getSms().getApiKey()) &&
                StringUtils.hasText(properties.getSms().getApiSecret());
    }
}

