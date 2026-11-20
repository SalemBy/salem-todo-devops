package github.salemby.tododevops;

import com.zaxxer.hikari.HikariDataSource;
import github.salemby.tododevops.identity.infrastructure.security.PasswordConfiguration;
import github.salemby.tododevops.support.GatedPasswordEncoder;
import github.salemby.tododevops.support.IsolatedPostgresSchema;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import javax.sql.DataSource;
import java.net.*;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import tools.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;

/** #44: no password hash may run while the request holds a pooled database connection. */
@SpringBootTest(classes = SalemTodoDevOpsApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("postgres-test")
@ContextConfiguration(initializers = IsolatedPostgresSchema.class)
@Import(PostgresPasswordHashingIT.GateConfiguration.class)
class PostgresPasswordHashingIT {
    @Autowired Environment environment;
    @Autowired JdbcTemplate jdbc;
    @Autowired DataSource dataSource;
    @Autowired GatedPasswordEncoder gate;
    final ObjectMapper json = new ObjectMapper();
    final List<String> emails = new ArrayList<>();
    final ExecutorService background = Executors.newSingleThreadExecutor();
    static final String PASSWORD = "A test only passphrase!";

    @TestConfiguration(proxyBeanMethods = false)
    static class GateConfiguration {
        @Bean @Primary GatedPasswordEncoder gatedPasswordEncoder() {
            return new GatedPasswordEncoder(new PasswordConfiguration().passwordEncoder());
        }
    }

    @AfterEach void cleanup() {
        gate.disarm();
        background.shutdownNow();
        for (String email : emails) jdbc.update("delete from accounts where email = ?", email);
    }

    int activeConnections() { return ((HikariDataSource) dataSource).getHikariPoolMXBean().getActiveConnections(); }
    String email() { String value = UUID.randomUUID() + "@example.com"; emails.add(value); return value; }

    @Test void loginHashesWithoutHoldingADatabaseConnection() throws Exception {
        String email = email(); var c = new Client(); c.csrf();
        assertEquals(201, c.register(email).statusCode());
        gate.arm();
        var login = background.submit(() -> c.login(email, PASSWORD));
        gate.awaitParked(Duration.ofSeconds(10));
        assertEquals(0, activeConnections(), "login held a pooled connection while hashing");
        gate.disarm();
        assertEquals(200, login.get(20, TimeUnit.SECONDS).statusCode());
    }

    @Test void unknownEmailLoginStillHashesWithoutAConnection() throws Exception {
        var c = new Client(); c.csrf();
        gate.arm();
        var login = background.submit(() -> c.login(UUID.randomUUID() + "@example.com", PASSWORD));
        gate.awaitParked(Duration.ofSeconds(10)); // proves the dummy hash still runs for unknown emails
        assertEquals(0, activeConnections(), "unknown-email login held a pooled connection while hashing");
        gate.disarm();
        assertEquals(401, login.get(20, TimeUnit.SECONDS).statusCode());
    }

    @Test void passwordChangeHashesWithoutHoldingAConnectionOrRowLock() throws Exception {
        String email = email(); var c = loggedIn(email);
        gate.arm();
        var change = background.submit(() -> c.changePassword(PASSWORD, "A replacement password!"));
        gate.awaitParked(Duration.ofSeconds(10)); // current-password check
        assertEquals(0, activeConnections(), "password change held a pooled connection while checking the current password");
        gate.releaseOne();
        gate.awaitParked(Duration.ofSeconds(10)); // new-password encode
        assertEquals(0, activeConnections(), "password change held a pooled connection while encoding the new password");
        gate.disarm();
        assertEquals(204, change.get(20, TimeUnit.SECONDS).statusCode());
        assertEquals(1L, jdbc.queryForObject("select credential_version from accounts where email = ?", Long.class, email));
    }

    @Test void disablingTheAccountMidChangeRejectsIt() throws Exception {
        String email = email(); var c = loggedIn(email);
        String hashBefore = jdbc.queryForObject("select password_hash from accounts where email = ?", String.class, email);
        gate.arm();
        var change = background.submit(() -> c.changePassword(PASSWORD, "A replacement password!"));
        gate.awaitParked(Duration.ofSeconds(10)); // current-password check
        jdbc.update("update accounts set active = false where email = ?", email);
        gate.disarm();
        var response = change.get(20, TimeUnit.SECONDS);
        assertEquals(401, response.statusCode(), response.body());
        assertTrue(response.headers().firstValue("Content-Type").orElseThrow().startsWith("application/problem+json"));
        assertEquals(0L, jdbc.queryForObject("select credential_version from accounts where email = ?", Long.class, email));
        assertEquals(hashBefore, jdbc.queryForObject("select password_hash from accounts where email = ?", String.class, email));
    }

    Client loggedIn(String email) throws Exception {
        var c = new Client(); c.csrf();
        assertEquals(201, c.register(email).statusCode());
        assertEquals(200, c.login(email, PASSWORD).statusCode());
        c.csrf(); return c;
    }

    final class Client {
        final CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        final HttpClient http = HttpClient.newBuilder().cookieHandler(cookies).connectTimeout(Duration.ofSeconds(5)).build();
        String token, header;
        HttpResponse<String> call(String method, String path, Object body, boolean csrf) throws Exception {
            var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + environment.getProperty("local.server.port") + path))
                .timeout(Duration.ofSeconds(30)).header("Content-Type", "application/json");
            if (csrf && token != null) request.header(header, token);
            return http.send(request.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build(), HttpResponse.BodyHandlers.ofString());
        }
        void csrf() throws Exception {
            var response = call("GET", "/auth/csrf", null, false);
            assertEquals(200, response.statusCode());
            var body = json.readTree(response.body()); token = body.get("token").asText(); header = body.get("headerName").asText();
        }
        HttpResponse<String> register(String email) throws Exception { return call("POST", "/auth/register", Map.of("email", email, "displayName", "Person", "password", PASSWORD), true); }
        HttpResponse<String> login(String email, String password) throws Exception { return call("POST", "/auth/login", Map.of("email", email, "password", password), true); }
        HttpResponse<String> changePassword(String current, String next) throws Exception { return call("POST", "/auth/password", Map.of("currentPassword", current, "newPassword", next), true); }
    }
}
