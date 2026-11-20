package github.salemby.tododevops.identity.infrastructure.ratelimit;

import github.salemby.tododevops.identity.infrastructure.ratelimit.RateLimitProperties.Limit;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

/**
 * Fixed-window counters shared by every instance. Redis errors propagate as Spring
 * DataAccessExceptions so callers can fail closed.
 */
@Component
@EnableConfigurationProperties(RateLimitProperties.class)
public class RedisRateLimiter {
    // Increment first, then check: concurrent requests cannot slip past the limit.
    @SuppressWarnings("rawtypes")
    private static final RedisScript<List> HIT = RedisScript.of("""
        local count = redis.call('INCR', KEYS[1])
        if count == 1 or redis.call('PTTL', KEYS[1]) < 0 then redis.call('PEXPIRE', KEYS[1], ARGV[1]) end
        return {count, redis.call('PTTL', KEYS[1])}
        """, List.class);

    private final StringRedisTemplate redis;
    private final String prefix;

    public RedisRateLimiter(StringRedisTemplate redis, RateLimitProperties properties) {
        this.redis = redis;
        this.prefix = properties.keyPrefix();
    }

    /** Counts one request; returns how long to wait when this request is over the limit. */
    public Optional<Duration> hit(String key, Limit limit) {
        List<?> result = redis.execute(HIT, List.of(prefix + key), String.valueOf(limit.window().toMillis()));
        long count = ((Number) result.get(0)).longValue(), ttlMillis = ((Number) result.get(1)).longValue();
        if (count <= limit.max()) return Optional.empty();
        return Optional.of(Duration.ofSeconds(Math.max(1, (ttlMillis + 999) / 1000)));
    }

    public void reset(String key) {
        redis.delete(prefix + key);
    }

    /** Redis never holds the address itself, only its SHA-256. */
    public static String emailKey(String normalizedEmail) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(normalizedEmail.getBytes(StandardCharsets.UTF_8));
            return "login:email:" + HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is required by every Java platform", ex);
        }
    }
}
