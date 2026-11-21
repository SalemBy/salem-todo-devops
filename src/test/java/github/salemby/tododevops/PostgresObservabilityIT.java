package github.salemby.tododevops;

import github.salemby.tododevops.support.AuthClient;
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
import java.util.Map;
import java.util.UUID;

import static github.salemby.tododevops.support.AuthClient.PASSWORD;
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

    @Test
    void prometheusExposesHttpAndPoolMetrics() throws Exception {
        assertEquals(200, get(port, "/").statusCode());
        String metrics = scrapeUntil("http_server_requests_seconds_count");
        assertTrue(metrics.contains("hikaricp_connections_active"), "Hikari pool metrics missing");
    }

    @Test
    void metricTagsCarryNoPersonalData() throws Exception {
        String email = "metrics-" + UUID.randomUUID() + "@example.com";
        var client = new AuthClient(port);
        assertEquals(200, client.csrf().statusCode());
        assertEquals(201, client.register(email).statusCode());
        assertEquals(200, client.login(email, PASSWORD).statusCode());
        var csrf = client.csrf();
        String token = JSON.readTree(csrf.body()).path("token").asString();
        String key = "M" + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
        var created = client.call("POST", "/projects", Map.of("name", "Metrics", "key", key));
        assertEquals(201, created.statusCode(), created.body());
        long id = JSON.readTree(created.body()).path("id").asLong();
        assertEquals(200, client.call("GET", "/projects/" + id, null).statusCode());
        var anonymous = new AuthClient(port);
        assertEquals(401, anonymous.call("GET", "/auth/me", null).statusCode());
        assertEquals(401, anonymous.call("GET", "/auth/login/extra", null).statusCode());
        anonymous.call("FOO", "/auth/login", null);

        String metrics = scrapeUntil("uri=\"/auth/me\"");
        assertTrue(metrics.contains("uri=\"/projects/{id}\""), "route templates are kept");
        assertTrue(metrics.contains("uri=\"/auth/login\""), "a public auth path rejected or served is named");
        for (String secret : List.of(email, client.cookie("JSESSIONID"), token, "/projects/" + id, "/auth/login/extra",
                "method=\"FOO\""))
            assertFalse(metrics.contains(secret), "metrics must not contain " + secret);
    }

    /** Metrics are recorded as each response completes, so the last request may land just after it is answered. */
    String scrapeUntil(String expected) throws Exception {
        String body = "";
        for (int i = 0; i < 20 && !body.contains(expected); i++) {
            if (i > 0) Thread.sleep(100);
            var response = management("/actuator/prometheus");
            assertEquals(200, response.statusCode());
            body = response.body();
        }
        assertTrue(body.contains(expected), "metrics never contained " + expected);
        return body;
    }

    HttpResponse<String> management(String path) throws Exception {
        return get(managementPort, path);
    }

    static HttpResponse<String> get(int port, String path) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).timeout(Duration.ofSeconds(15)).GET();
        return HTTP.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }
}
