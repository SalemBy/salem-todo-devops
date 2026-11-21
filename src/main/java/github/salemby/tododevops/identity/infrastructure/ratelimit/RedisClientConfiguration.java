package github.salemby.tododevops.identity.infrastructure.ratelimit;

import io.lettuce.core.ClientOptions;
import org.springframework.boot.data.redis.autoconfigure.LettuceClientConfigurationBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Redis client behavior shared by sessions and rate limits. */
@Configuration
class RedisClientConfiguration {
    /**
     * While the connection is down, reject commands at once instead of queueing them until the command
     * timeout: a Redis restart then costs each request milliseconds, not a server thread for a second.
     * Keeps every other client option Spring Boot set (connect timeout included).
     */
    @Bean
    LettuceClientConfigurationBuilderCustomizer rejectCommandsWhileDisconnected() {
        return builder -> {
            var current = builder.build().getClientOptions().orElseGet(ClientOptions::create);
            builder.clientOptions(current.mutate().disconnectedBehavior(ClientOptions.DisconnectedBehavior.REJECT_COMMANDS).build());
        };
    }
}
