package github.salemby.tododevops.identity.infrastructure.ratelimit;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/** Authentication rate limits; invalid values fail startup. */
@Validated
@ConfigurationProperties("app.rate-limit")
public record RateLimitProperties(
    @NotBlank String keyPrefix,
    @Valid @NotNull Limit loginPerAddress,
    @Valid @NotNull Limit loginPerEmail,
    @Valid @NotNull Limit registerPerAddress,
    @Valid @NotNull Limit sessionsPerAddress,
    @Valid @NotNull Limit passwordChangePerLogin
) {
    public record Limit(@Min(1) int max, @NotNull Duration window) {
        public Limit {
            if (window != null && (window.isZero() || window.isNegative()))
                throw new IllegalArgumentException("Rate-limit window must be positive");
        }
    }
}
