package github.salemby.tododevops.identity.application;

import github.salemby.tododevops.identity.infrastructure.ratelimit.RateLimitProperties;
import github.salemby.tododevops.identity.infrastructure.ratelimit.RedisRateLimiter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;
import org.springframework.web.ErrorResponseException;

import java.time.Duration;
import java.util.Optional;

/** The per-key authentication limits (#45, #55, #71): a throttled request costs one Redis call and nothing else. */
@Component
public class Throttle {
    private static final Logger log = LoggerFactory.getLogger(Throttle.class);
    private final RedisRateLimiter limiter;

    public Throttle(RedisRateLimiter limiter) {
        this.limiter = limiter;
    }

    /** Counts one hit; over the limit it throws 429 with Retry-After, and 503 when the store is down. */
    public void hit(String key, RateLimitProperties.Limit limit) {
        Optional<Duration> retryAfter;
        try {
            retryAfter = limiter.hit(key, limit);
        } catch (DataAccessException ex) {
            log.warn("Rate-limit store unavailable; failing closed: {}", ex.getMessage());
            log.debug("Rate-limit store failure", ex);
            throw problem(HttpStatus.SERVICE_UNAVAILABLE, "Authentication is temporarily unavailable.", ex);
        }
        if (retryAfter.isPresent()) {
            var tooMany = problem(HttpStatus.TOO_MANY_REQUESTS, "Too many requests. Try again later.", null);
            tooMany.getHeaders().set(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfter.get().toSeconds()));
            throw tooMany;
        }
    }

    private static ErrorResponseException problem(HttpStatus status, String detail, Throwable cause) {
        var problem = new ErrorResponseException(status, ProblemDetail.forStatusAndDetail(status, detail), cause);
        problem.getHeaders().setCacheControl("no-store");
        return problem;
    }
}
