package module.notification.services.servicesImpl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import module.notification.enums.ChannelType;
import module.notification.enums.NotificationType;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationMetricsService {

    private final Map<String, AtomicLong> counters = new ConcurrentHashMap<>();

    public void recordNotificationSent(ChannelType channel, NotificationType type) {
        String key = String.format("sent_%s_%s",
        channel != null ? channel.name().toLowerCase() : "unknown",
        type != null ? type.name().toLowerCase() : "unknown");

        counters.computeIfAbsent(key, k -> new AtomicLong(0)).incrementAndGet();
        log.debug("Notification envoyée - Canal: {}, Type: {}", channel, type);
    }

    public void recordNotificationFailed(ChannelType channel, NotificationType type) {
        String key = String.format("failed_%s_%s",
        channel != null ? channel.name().toLowerCase() : "unknown",
        type != null ? type.name().toLowerCase() : "unknown");

        counters.computeIfAbsent(key, k -> new AtomicLong(0)).incrementAndGet();
        log.debug("Notification échouée - Canal: {}, Type: {}", channel, type);
    }

    public void recordNotificationRead(ChannelType channel, NotificationType type) {
        String key = String.format("read_%s_%s",
        channel != null ? channel.name().toLowerCase() : "unknown",
        type != null ? type.name().toLowerCase() : "unknown");

        counters.computeIfAbsent(key, k -> new AtomicLong(0)).incrementAndGet();
        log.debug("Notification lue - Type: {}", type);
    }

    public long getCounter(String key) {
        return counters.getOrDefault(key, new AtomicLong(0)).get();
    }

    public Map<String, Long> getAllCounters() {
        Map<String, Long> result = new ConcurrentHashMap<>();
        counters.forEach((key, value) -> result.put(key, value.get()));
        return result;
    }

    public void resetCounters() {
        counters.clear();
        log.info("Compteurs de métriques réinitialisés");
    }
}
