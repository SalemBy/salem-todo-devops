package github.salemby.tododevops.identity.infrastructure.security;

import github.salemby.tododevops.identity.application.AccountPrincipal;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.serializer.JdkSerializationRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.web.csrf.DefaultCsrfToken;

import java.net.InetSocketAddress;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RedisSessionConfigurationTests {
    private final RedisSerializer<Object> serializer = new RedisSessionConfiguration().springSessionDefaultRedisSerializer();

    @Test
    void sessionContentsRoundTrip() {
        var principal = new AccountPrincipal(7L, 2, Instant.parse("2026-11-20T10:00:00Z"));
        var context = new SecurityContextImpl(UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of()));
        var restored = (SecurityContextImpl) serializer.deserialize(serializer.serialize(context));
        assertEquals(principal, restored.getAuthentication().getPrincipal());
        var token = new DefaultCsrfToken("X-CSRF-TOKEN", "_csrf", "value");
        assertEquals("value", ((DefaultCsrfToken) serializer.deserialize(serializer.serialize(token))).getToken());
        assertEquals(1_700_000_000_000L, serializer.deserialize(serializer.serialize(1_700_000_000_000L)));
    }

    @Test
    void classesOutsideTheAllowListAreNotDeserialized() {
        // Anything that can write to Redis could plant a serialized object; only session classes may load.
        byte[] planted = new JdkSerializationRedisSerializer().serialize(new InetSocketAddress("127.0.0.1", 1));
        assertNull(serializer.deserialize(planted));
    }
}
