package module.notification.services.servicesImpl;

import lombok.extern.slf4j.Slf4j;
import module.notification.enums.ChannelType;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

@Service
@Slf4j
public class CircuitBreakerService {

    private final Map<ChannelType, CircuitBreakerState> circuitBreakers = new ConcurrentHashMap<>();

    // Configuration par canal
    private final Map<ChannelType, CircuitBreakerConfig> configs = Map.of(
            ChannelType.EMAIL, new CircuitBreakerConfig(5, Duration.ofMinutes(5), Duration.ofSeconds(30)),
            ChannelType.SMS, new CircuitBreakerConfig(3, Duration.ofMinutes(10), Duration.ofMinutes(1)),
            ChannelType.PUSH, new CircuitBreakerConfig(10, Duration.ofMinutes(3), Duration.ofSeconds(45)),
            ChannelType.WEB, new CircuitBreakerConfig(15, Duration.ofMinutes(2), Duration.ofSeconds(20)),
            ChannelType.WEBSOCKET, new CircuitBreakerConfig(20, Duration.ofMinutes(1), Duration.ofSeconds(15))
    );

    public boolean canSend(ChannelType channel) {
        CircuitBreakerState state = getOrCreateState(channel);
        CircuitBreakerConfig config = configs.get(channel);

        LocalDateTime now = LocalDateTime.now();

        switch (state.getStatus()) {
            case CLOSED:
                return true;

            case OPEN:
                // Vérifier si on peut passer en half-open
                if (now.isAfter(state.getLastFailure().plus(config.getTimeoutDuration()))) {
                    state.setStatus(CircuitBreakerStatus.HALF_OPEN);
                    state.resetFailureCount();
                    log.info("Circuit breaker {} passé de OPEN à HALF_OPEN", channel);
                    return true;
                }
                return false;

            case HALF_OPEN:
                // Limite les tentatives en half-open
                return state.getFailureCount().get() < 3;

            default:
                return false;
        }
    }

    public void onSuccess(ChannelType channel) {
        CircuitBreakerState state = getOrCreateState(channel);

        if (state.getStatus() == CircuitBreakerStatus.HALF_OPEN) {
            state.setStatus(CircuitBreakerStatus.CLOSED);
            state.resetFailureCount();
            log.info("Circuit breaker {} fermé après succès", channel);
        } else if (state.getStatus() == CircuitBreakerStatus.CLOSED) {
            state.resetFailureCount();
        }
    }

    public void onFailure(ChannelType channel, Exception error) {
        CircuitBreakerState state = getOrCreateState(channel);
        CircuitBreakerConfig config = configs.get(channel);

        state.incrementFailureCount();
        state.setLastFailure(LocalDateTime.now());

        int failures = state.getFailureCount().get();

        if (failures >= config.getFailureThreshold() &&
                state.getStatus() != CircuitBreakerStatus.OPEN) {
            state.setStatus(CircuitBreakerStatus.OPEN);
            log.warn("Circuit breaker {} ouvert après {} échecs. Erreur: {}",
                    channel, failures, error.getMessage());
        }
    }

    public Map<ChannelType, CircuitBreakerInfo> getCircuitBreakerStatus() {
        Map<ChannelType, CircuitBreakerInfo> status = new ConcurrentHashMap<>();

        circuitBreakers.forEach((channel, state) -> {
            status.put(channel, CircuitBreakerInfo.builder()
                    .status(state.getStatus())
                    .failureCount(state.getFailureCount().get())
                    .lastFailure(state.getLastFailure())
                    .build());
        });

        return status;
    }

    private CircuitBreakerState getOrCreateState(ChannelType channel) {
        return circuitBreakers.computeIfAbsent(channel, k -> new CircuitBreakerState());
    }

    // Classes internes
    private static class CircuitBreakerState {
        private volatile CircuitBreakerStatus status = CircuitBreakerStatus.CLOSED;
        private final AtomicInteger failureCount = new AtomicInteger(0);
        private final AtomicReference<LocalDateTime> lastFailure = new AtomicReference<>();

        public CircuitBreakerStatus getStatus() { return status; }
        public void setStatus(CircuitBreakerStatus status) { this.status = status; }
        public AtomicInteger getFailureCount() { return failureCount; }
        public void incrementFailureCount() { failureCount.incrementAndGet(); }
        public void resetFailureCount() { failureCount.set(0); }
        public LocalDateTime getLastFailure() { return lastFailure.get(); }
        public void setLastFailure(LocalDateTime time) { lastFailure.set(time); }
    }

    private static class CircuitBreakerConfig {
        private final int failureThreshold;
        private final Duration timeoutDuration;
        private final Duration retryInterval;

        public CircuitBreakerConfig(int failureThreshold, Duration timeoutDuration, Duration retryInterval) {
            this.failureThreshold = failureThreshold;
            this.timeoutDuration = timeoutDuration;
            this.retryInterval = retryInterval;
        }

        public int getFailureThreshold() { return failureThreshold; }
        public Duration getTimeoutDuration() { return timeoutDuration; }
        public Duration getRetryInterval() { return retryInterval; }
    }

    public enum CircuitBreakerStatus {
        CLOSED, OPEN, HALF_OPEN
    }

    @lombok.Builder
    @lombok.Data
    public static class CircuitBreakerInfo {
        private CircuitBreakerStatus status;
        private int failureCount;
        private LocalDateTime lastFailure;
    }
}