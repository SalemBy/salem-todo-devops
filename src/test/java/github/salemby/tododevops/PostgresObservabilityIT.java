package github.salemby.tododevops;

import github.salemby.tododevops.support.IsolatedPostgresSchema;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Actuator lives on a local management port; the public port exposes none of it (#51). */
@SpringBootTest(classes = SalemTodoDevOpsApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("postgres-test")
@ContextConfiguration(initializers = IsolatedPostgresSchema.class)
class PostgresObservabilityIT {
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private static final ObjectMapper JSON = new ObjectMapper();

    @Value("${local.server.port}") int port;
    @Value("${local.management.port}") int managementPort;

    @Test
    void probesReportOnlyTheStatus() throws Exception {
        for (String path : List.of("/actuator/health/liveness", "/actuator/health/readiness")) {
            var response = management(path);
            assertEquals(200, response.statusCode(), path);
            assertEquals("{\"status\":\"UP\"}", response.body(), path);
            assertTrue(response.headers().firstValue("Set-Cookie").isEmpty(), path + " must not create a session");
        }
        // The overall endpoint also lists the probe group names, never components or details.
        var overall = management("/actuator/health");
        assertEquals(200, overall.statusCode());
        assertEquals("{\"groups\":[\"liveness\",\"readiness\"],\"status\":\"UP\"}", overall.body());
        assertTrue(overall.headers().firstValue("Set-Cookie").isEmpty());
    }

    @Test
    void publicPortRefusesActuator() throws Exception {
        for (String path : List.of("/actuator/health", "/actuator/prometheus"))
            assertEquals(401, get(port, path).statusCode(), path);
    }

    @Test
    void endpointsThatAreNotExposedAreRefused() throws Exception {
        // Not exposed, so outside the management chain: the application's rules refuse them (no session).
        for (String path : List.of("/actuator/env", "/actuator/beans", "/actuator/heapdump"))
            assertEquals(401, management(path).statusCode(), path);
    }

    @Test
    void infoShowsBuildAndCommit() throws Exception {
        var info = JSON.readTree(management("/actuator/info").body());
        assertFalse(info.path("build").path("version").asString("").isEmpty(), info.toString());
        assertFalse(info.path("git").path("commit").path("id").asString("").isEmpty(), info.toString());
    }

    HttpResponse<String> management(String path) throws Exception {
        return get(managementPort, path);
    }

    static HttpResponse<String> get(int port, String path) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).timeout(Duration.ofSeconds(15)).GET();
        return HTTP.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }
}
