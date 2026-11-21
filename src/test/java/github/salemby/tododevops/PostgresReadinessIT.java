package github.salemby.tododevops;

import github.salemby.tododevops.support.SharedSchemaApps;
import github.salemby.tododevops.support.TcpRelay;
import org.junit.jupiter.api.Test;
import org.springframework.context.ConfigurableApplicationContext;

import java.net.URI;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Readiness follows PostgreSQL and Redis; liveness never does, so an outage never restarts the app (#51). */
class PostgresReadinessIT {
    private static final String UP = "{\"status\":\"UP\"}", DOWN = "{\"status\":\"DOWN\"}";

    @Test
    void readinessIsDownWhilePostgresIsCut() throws Exception {
        String url = System.getenv("TEST_DB_URL");
        int dbPort = URI.create(url.substring("jdbc:".length())).getPort();
        try (var relay = new TcpRelay(dbPort); var apps = new SharedSchemaApps()) {
            var app = apps.start("spring.datasource.url=" + url.replace(":" + dbPort + "/", ":" + relay.port() + "/"),
                "spring.datasource.hikari.connection-timeout=2000");
            assertProbe(app, "readiness", 200, UP);
            relay.close();
            assertProbe(app, "readiness", 503, DOWN);
            assertProbe(app, "liveness", 200, UP);
        }
    }

    @Test
    void readinessIsDownWhileRedisIsUnreachable() throws Exception {
        try (var apps = new SharedSchemaApps()) {
            var app = apps.start("spring.data.redis.port=1");
            assertProbe(app, "readiness", 503, DOWN);
            assertProbe(app, "liveness", 200, UP);
        }
    }

    private static void assertProbe(ConfigurableApplicationContext app, String probe, int status, String body) throws Exception {
        int port = Integer.parseInt(app.getEnvironment().getRequiredProperty("local.management.port"));
        HttpResponse<String> response = PostgresObservabilityIT.get(port, "/actuator/health/" + probe);
        assertEquals(status, response.statusCode(), probe + ": " + response.body());
        assertEquals(body, response.body(), probe);
    }
}
