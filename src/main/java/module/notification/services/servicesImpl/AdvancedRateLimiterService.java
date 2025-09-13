package module.notification.services.servicesImpl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import module.notification.config.NotificationProperties;
import module.notification.enums.ChannelType;
import module.notification.enums.NotificationType;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdvancedRateLimiterService {

    private final NotificationProperties properties;
    private final RedisTemplate<String, String> redisTemplate;

    // Fallback en mémoire si Redis n'est pas disponible
    private final Map<String, RateLimitEntry> memoryCache = new ConcurrentHashMap<>();

    // Script Lua pour sliding window rate limiting
    private static final String SLIDING_WINDOW_SCRIPT =
            "local key = KEYS[1]\n" +
                    "local window = tonumber(ARGV[1])\n" +
                    "local limit = tonumber(ARGV[2])\n" +
                    "local current_time = tonumber(ARGV[3])\n" +
                    "local expire_time = current_time - window\n" +
                    "redis.call('zremrangebyscore', key, 0, expire_time)\n" +
                    "local current_count = redis.call('zcard', key)\n" +
                    "if current_count < limit then\n" +
                    "    redis.call('zadd', key, current_time, current_time)\n" +
                    "    redis.call('expire', key, window)\n" +
                    "    return {1, limit - current_count - 1}\n" +
                    "else\n" +
                    "    return {0, 0}\n" +
                    "end";

    /**
     * Vérifie si une requête est autorisée selon les règles de rate limiting
     */
    public RateLimitResult isAllowed(String userId, ChannelType channel, NotificationType type) {
        if (!properties.getRateLimit().isEnabled()) {
            return RateLimitResult.allowed();
        }

        // Vérifier les limites globales
        RateLimitResult globalLimit = checkGlobalLimits(userId);
        if (!globalLimit.isAllowed()) {
            return globalLimit;
        }

        // Vérifier les limites par canal
        RateLimitResult channelLimit = checkChannelLimits(userId, channel);
        if (!channelLimit.isAllowed()) {
            return channelLimit;
        }

        // Vérifier les limites par type
        RateLimitResult typeLimit = checkTypeLimits(userId, type);
        if (!typeLimit.isAllowed()) {
            return typeLimit;
        }

        return RateLimitResult.allowed();
    }

    private RateLimitResult checkGlobalLimits(String userId) {
        // Limite par minute
        String minuteKey = "rate_limit:global:" + userId + ":minute";
        RateLimitResult minuteResult = checkSlidingWindow(
                minuteKey,
                Duration.ofMinutes(1),
                properties.getRateLimit().getMaxNotificationsPerUserPerMinute()
        );

        if (!minuteResult.isAllowed()) {
            return RateLimitResult.denied("Limite globale par minute dépassée", minuteResult.getRetryAfter());
        }

        // Limite par heure
        String hourKey = "rate_limit:global:" + userId + ":hour";
        RateLimitResult hourResult = checkSlidingWindow(
                hourKey,
                Duration.ofHours(1),
                properties.getRateLimit().getMaxNotificationsPerUserPerHour()
        );

        if (!hourResult.isAllowed()) {
            return RateLimitResult.denied("Limite globale par heure dépassée", hourResult.getRetryAfter());
        }

        return RateLimitResult.allowed();
    }

    private RateLimitResult checkChannelLimits(String userId, ChannelType channel) {
        int hourlyLimit = getChannelHourlyLimit(channel);
        if (hourlyLimit <= 0) {
            return RateLimitResult.allowed();
        }

        String key = "rate_limit:channel:" + channel.name() + ":" + userId + ":hour";
        return checkSlidingWindow(key, Duration.ofHours(1), hourlyLimit);
    }

    private RateLimitResult checkTypeLimits(String userId, NotificationType type) {
        // Limites spécifiques par type (configurable)
        String key = "rate_limit:type:" + type.name() + ":" + userId + ":hour";
        return checkSlidingWindow(key, Duration.ofHours(1), 50); // Limite par défaut
    }

    private RateLimitResult checkSlidingWindow(String key, Duration window, int limit) {
        try {
            // Utiliser Redis si disponible
            if (isRedisAvailable()) {
                return checkSlidingWindowRedis(key, window, limit);
            } else {
                return checkSlidingWindowMemory(key, window, limit);
            }
        } catch (Exception e) {
            log.error("Erreur lors de la vérification du rate limiting pour {}: {}", key, e.getMessage());
            // En cas d'erreur, autoriser par défaut (fail-open)
            return RateLimitResult.allowed();
        }
    }

    @SuppressWarnings("unchecked")
    private RateLimitResult checkSlidingWindowRedis(String key, Duration window, int limit) {
        long currentTime = System.currentTimeMillis();
        long windowMillis = window.toMillis();

        RedisScript<List> script = RedisScript.of(SLIDING_WINDOW_SCRIPT, List.class);
        List<Long> result = redisTemplate.execute(
                script,
                Collections.singletonList(key),
                String.valueOf(windowMillis / 1000), // Convertir en secondes
                String.valueOf(limit),
                String.valueOf(currentTime)
        );

        if (result != null && result.size() >= 2) {
            boolean allowed = result.get(0) == 1L;
            long remaining = result.get(1);

            if (allowed) {
                return RateLimitResult.allowed(remaining);
            } else {
                // Calculer le temps d'attente
                Duration retryAfter = Duration.ofMillis(windowMillis / limit);
                return RateLimitResult.denied("Rate limit dépassé", retryAfter);
            }
        }

        return RateLimitResult.allowed();
    }

    private RateLimitResult checkSlidingWindowMemory(String key, Duration window, int limit) {
        RateLimitEntry entry = memoryCache.computeIfAbsent(key, k -> new RateLimitEntry());

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime windowStart = now.minus(window);

        synchronized (entry) {
            // Nettoyer les anciennes entrées
            entry.getTimestamps().removeIf(timestamp -> timestamp.isBefore(windowStart));

            if (entry.getTimestamps().size() < limit) {
                entry.getTimestamps().add(now);
                return RateLimitResult.allowed(limit - entry.getTimestamps().size());
            } else {
                Duration retryAfter = Duration.between(now, entry.getTimestamps().get(0).plus(window));
                return RateLimitResult.denied("Rate limit dépassé", retryAfter);
            }
        }
    }

    private boolean isRedisAvailable() {
        try {
            redisTemplate.hasKey("test");
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private int getChannelHourlyLimit(ChannelType channel) {
        switch (channel) {
            case EMAIL:
                return properties.getRateLimit().getMaxEmailPerHour();
            case SMS:
                return properties.getRateLimit().getMaxSmsPerHour();
            case PUSH:
                return properties.getRateLimit().getMaxPushPerHour();
            case WEB:
                return properties.getRateLimit().getMaxWebPerHour();
            case WEBSOCKET:
                return 1000; // Limite par défaut pour WebSocket
            default:
                return 100;
        }
    }

    /**
     * Obtient les statistiques de rate limiting pour un utilisateur
     */
    public Map<String, Object> getUserRateLimitStats(String userId) {
        Map<String, Object> stats = new ConcurrentHashMap<>();

        for (ChannelType channel : ChannelType.values()) {
            String key = "rate_limit:channel:" + channel.name() + ":" + userId + ":hour";
            int currentCount = getCurrentCount(key);
            int limit = getChannelHourlyLimit(channel);

            stats.put(channel.name().toLowerCase(), Map.of(
                    "current", currentCount,
                    "limit", limit,
                    "remaining", Math.max(0, limit - currentCount)
            ));
        }

        return stats;
    }

    /**
     * Réinitialise les compteurs pour un utilisateur (admin only)
     */
    public void resetUserLimits(String userId) {
        try {
            if (isRedisAvailable()) {
                String pattern = "rate_limit:*:" + userId + ":*";
                redisTemplate.delete(redisTemplate.keys(pattern));
            } else {
                memoryCache.entrySet().removeIf(entry -> entry.getKey().contains(userId));
            }
            log.info("Rate limits réinitialisés pour l'utilisateur: {}", userId);
        } catch (Exception e) {
            log.error("Erreur lors de la réinitialisation des rate limits pour {}: {}", userId, e.getMessage());
        }
    }

    private int getCurrentCount(String key) {
        try {
            if (isRedisAvailable()) {
                return redisTemplate.opsForZSet().zCard(key).intValue();
            } else {
                RateLimitEntry entry = memoryCache.get(key);
                return entry != null ? entry.getTimestamps().size() : 0;
            }
        } catch (Exception e) {
            return 0;
        }
    }

    // Classes utilitaires
    @lombok.Data
    @lombok.AllArgsConstructor
    public static class RateLimitResult {
        private boolean allowed;
        private String reason;
        private Duration retryAfter;
        private long remaining;

        public static RateLimitResult allowed() {
            return new RateLimitResult(true, null, null, -1);
        }

        public static RateLimitResult allowed(long remaining) {
            return new RateLimitResult(true, null, null, remaining);
        }

        public static RateLimitResult denied(String reason, Duration retryAfter) {
            return new RateLimitResult(false, reason, retryAfter, 0);
        }
    }

    private static class RateLimitEntry {
        private final List<LocalDateTime> timestamps = new java.util.ArrayList<>();

        public List<LocalDateTime> getTimestamps() {
            return timestamps;
        }
    }
}