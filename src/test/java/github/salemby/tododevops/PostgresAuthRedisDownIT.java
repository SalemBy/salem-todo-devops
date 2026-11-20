package github.salemby.tododevops;

import github.salemby.tododevops.support.AuthClient;
import github.salemby.tododevops.support.IsolatedPostgresSchema;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;

import java.net.http.HttpResponse;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Fail closed: with Redis unreachable, limited routes return 503 and other routes keep working. */
@SpringBootTest(classes = SalemTodoDevOpsApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("postgres-test")
@ContextConfiguration(initializers = IsolatedPostgresSchema.class)
@TestPropertySource(properties = "spring.data.redis.port=1")
class PostgresAuthRedisDownIT {
    @Value("${local.server.port}") int port;

    @Test
    void limitedRoutesFailClosedWith503() throws Exception {
        var client = new AuthClient(port);
        unavailable(client.csrf());
        unavailable(client.login("ana@example.com", "any password at all"));
        unavailable(client.register("ana@example.com"));
    }

    @Test
    void statusRouteStillWorks() throws Exception {
        assertEquals(200, new AuthClient(port).call("GET", "/", null).statusCode());
    }

    private static void unavailable(HttpResponse<String> response) {
        assertEquals(503, response.statusCode(), response.body());
        assertEquals("application/problem+json", response.headers().firstValue("Content-Type").orElseThrow());
        assertTrue(response.headers().firstValue("Retry-After").isEmpty());
        assertTrue(response.body().contains("\"detail\":\"Authentication is temporarily unavailable.\""), response.body());
    }
}
