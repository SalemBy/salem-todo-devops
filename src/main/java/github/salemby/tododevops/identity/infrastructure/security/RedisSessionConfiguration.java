package github.salemby.tododevops.identity.infrastructure.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.ConfigurableObjectInputStream;
import org.springframework.core.serializer.support.DeserializingConverter;
import org.springframework.core.serializer.support.SerializingConverter;
import org.springframework.data.redis.serializer.JdkSerializationRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.SerializationException;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import org.springframework.session.data.redis.RedisSessionRepository;

import java.io.IOException;
import java.io.ObjectInputFilter;

/** Hardening for the Redis session store (Spring Session). */
@Configuration
class RedisSessionConfiguration {
    private static final Logger log = LoggerFactory.getLogger(RedisSessionConfiguration.class);

    /** Only what a session holds may be deserialized; anything else planted in Redis is refused. */
    static final ObjectInputFilter SESSION_CLASSES = ObjectInputFilter.Config.createFilter(
        "java.lang.*;java.util.*;java.time.*;org.springframework.security.**;org.springframework.session.**;"
            + "github.salemby.tododevops.**;!*");

    /**
     * Spring Session's value serializer. A stored attribute that cannot be read (an upgrade changed a class,
     * or the class is not allowed) reads as absent, so the caller is simply logged out.
     */
    @Bean("springSessionDefaultRedisSerializer")
    RedisSerializer<Object> springSessionDefaultRedisSerializer() {
        // The application's class loader, not Spring's default: under DevTools the app classes live in a
        // restart class loader, and a principal read with another loader fails `instanceof` and logs the user out.
        ClassLoader loader = RedisSessionConfiguration.class.getClassLoader();
        var jdk = new JdkSerializationRedisSerializer(new SerializingConverter(), new DeserializingConverter(input -> {
            var in = new ConfigurableObjectInputStream(input, loader);
            in.setObjectInputFilter(SESSION_CLASSES);
            try {
                return in.readObject();
            } catch (ClassNotFoundException ex) {
                throw new IOException(ex);
            }
        }));
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

    /**
     * Tolerates sessions that disappear while a request uses them. A logout, a session-ID rotation or an
     * expiry on another request deletes the Redis key; Spring Session then fails the save of every request
     * that still holds the old session, and a save racing the delete can write back a partial hash that
     * cannot be read. The invalidation wins: the late save is dropped, and a partial hash reads as no session.
     */
    @Bean
    static BeanPostProcessor invalidationTolerantSessionRepository() {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String name) {
                return bean instanceof RedisSessionRepository repository ? new InvalidationTolerant(repository) : bean;
            }
        };
    }

    private static final class InvalidationTolerant implements SessionRepository<Session> {
        private final SessionRepository<Session> delegate;

        @SuppressWarnings("unchecked")
        InvalidationTolerant(RedisSessionRepository repository) {
            // RedisSessionRepository's session type is package-private; every session passed back is one it created.
            this.delegate = (SessionRepository<Session>) (SessionRepository<?>) repository;
        }

        @Override
        public Session createSession() {
            return delegate.createSession();
        }

        @Override
        public void save(Session session) {
            try {
                delegate.save(session);
            } catch (IllegalStateException ex) {
                if (!"Session was invalidated".equals(ex.getMessage())) throw ex;
                log.debug("Dropping the save of a session deleted by a concurrent request");
            }
        }

        @Override
        public Session findById(String id) {
            try {
                return delegate.findById(id);
            } catch (IllegalStateException ex) { // a partial hash written by a save that raced a delete
                log.warn("Discarding an incomplete stored session: {}", ex.getMessage());
                delegate.deleteById(id);
                return null;
            }
        }

        @Override
        public void deleteById(String id) {
            delegate.deleteById(id);
        }
    }
}
