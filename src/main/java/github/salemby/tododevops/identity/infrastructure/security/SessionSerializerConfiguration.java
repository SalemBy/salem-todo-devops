package github.salemby.tododevops.identity.infrastructure.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.serializer.JdkSerializationRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.SerializationException;

/**
 * Spring Session's value serializer. A stored attribute that can no longer be read (for example after
 * an upgrade changes a Spring Security class) reads as absent, so the caller is simply logged out.
 */
@Configuration
class SessionSerializerConfiguration {
    private static final Logger log = LoggerFactory.getLogger(SessionSerializerConfiguration.class);

    @Bean("springSessionDefaultRedisSerializer")
    RedisSerializer<Object> springSessionDefaultRedisSerializer() {
        // The application's class loader, not Spring's default: under DevTools the app classes live in a
        // restart class loader, and a principal read with another loader fails `instanceof` and logs the user out.
        var jdk = new JdkSerializationRedisSerializer(SessionSerializerConfiguration.class.getClassLoader());
        return new RedisSerializer<>() {
            @Override
            public byte[] serialize(Object value) {
                return jdk.serialize(value);
            }

            @Override
            public Object deserialize(byte[] bytes) {
                try {
                    return jdk.deserialize(bytes);
                } catch (SerializationException ex) {
                    log.warn("Discarding an unreadable session attribute: {}", ex.getMessage());
                    log.debug("Unreadable session attribute", ex);
                    return null;
                }
            }
        };
    }
}
