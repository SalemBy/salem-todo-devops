package github.salemby.tododevops;

import github.salemby.tododevops.support.AuthClient;
import github.salemby.tododevops.support.IsolatedPostgresSchema;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static github.salemby.tododevops.support.AuthClient.PASSWORD;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Per-address limits. Every test connects from 127.0.0.1, so each one gets a fresh context
 * and with it a fresh random key prefix.
 */
@SpringBootTest(classes = SalemTodoDevOpsApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("postgres-test")
@ContextConfiguration(initializers = IsolatedPostgresSchema.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@TestPropertySource(properties = {"app.rate-limit.login-per-address.max=3", "app.rate-limit.register-per-address.max=2",
    "app.rate-limit.sessions-per-address.max=2", "app.rate-limit.password-change-per-address.max=2",
    "app.rate-limit.verification-email-per-address.max=2", "app.rate-limit.verify-per-address.max=2"})
class PostgresAuthAddressLimitIT {
    @Autowired JdbcTemplate jdbc;
    @Value("${local.server.port}") int port;
    final List<String> emails = new ArrayList<>();

    @AfterEach void cleanup() { for (String email : emails) jdbc.update("delete from accounts where email = ?", email); }

    String email() { String value = UUID.randomUUID() + "@example.com"; emails.add(value); return value; }

    @Test
    void loginAddressLimitHoldsAcrossEmails() throws Exception {
        var client = new AuthClient(port);
        assertEquals(200, client.csrf().statusCode());
        for (int i = 0; i < 3; i++) assertEquals(401, client.login(UUID.randomUUID() + "@example.com", "wrong password!").statusCode());
        var throttled = client.login(UUID.randomUUID() + "@example.com", "wrong password!");
        assertEquals(429, throttled.statusCode());
        assertTrue(throttled.headers().firstValue("Retry-After").isPresent());
        assertEquals("application/problem+json", throttled.headers().firstValue("Content-Type").orElseThrow());
    }

    @Test
    void registrationAddressLimit() throws Exception {
        var client = new AuthClient(port);
        assertEquals(200, client.csrf().statusCode());
        assertEquals(201, client.register(email()).statusCode());
        assertEquals(201, client.register(email()).statusCode());
        assertEquals(429, client.register(email()).statusCode());
    }

    @Test
    void verificationEmailAddressLimitHoldsAcrossEmails() throws Exception {
        var client = new AuthClient(port);
        assertEquals(200, client.csrf().statusCode());
        for (int i = 0; i < 2; i++)
            assertEquals(202, client.call("POST", "/auth/verification-email", java.util.Map.of("email", email())).statusCode());
        var throttled = client.call("POST", "/auth/verification-email", java.util.Map.of("email", email()));
        assertEquals(429, throttled.statusCode());
        assertTrue(throttled.headers().firstValue("Retry-After").isPresent());
    }

    @Test
    void verifyAddressLimitCapsPasswordChecks() throws Exception {
        var client = new AuthClient(port);
        assertEquals(200, client.csrf().statusCode());
        var body = java.util.Map.of("token", "unknown-token", "password", PASSWORD);
        for (int i = 0; i < 2; i++) assertEquals(400, client.call("POST", "/auth/verify", body).statusCode());
        var throttled = client.call("POST", "/auth/verify", body);
        assertEquals(429, throttled.statusCode());
        assertTrue(throttled.headers().firstValue("Retry-After").isPresent());
    }

    @Test
    void anonymousCsrfIsLimited() throws Exception {
        assertEquals(200, new AuthClient(port).csrf().statusCode());
        assertEquals(200, new AuthClient(port).csrf().statusCode());
        var throttled = new AuthClient(port).csrf();
        assertEquals(429, throttled.statusCode());
        assertTrue(throttled.headers().firstValue("Retry-After").isPresent());
        assertTrue(throttled.body().contains("\"detail\":\"Too many requests. Try again later.\""), throttled.body());
    }

    @Test
    void passwordChangeAddressLimitHoldsAcrossLogins() throws Exception {
        // A fresh login gets a fresh per-login count; the address count caps the hashes anyway.
        var client = new AuthClient(port);
        assertEquals(200, client.csrf().statusCode());
        String email = email();
        assertEquals(201, client.register(email).statusCode());
        assertEquals(200, client.login(email, PASSWORD).statusCode());
        client.csrf();
        for (int i = 0; i < 2; i++) assertEquals(400, changePassword(client).statusCode());
        assertEquals(200, client.login(email, PASSWORD).statusCode());
        client.csrf();
        var throttled = changePassword(client);
        assertEquals(429, throttled.statusCode());
        assertTrue(throttled.headers().firstValue("Retry-After").isPresent());
        assertTrue(throttled.body().contains("\"detail\":\"Too many requests. Try again later.\""), throttled.body());
    }

    private static java.net.http.HttpResponse<String> changePassword(AuthClient client) throws Exception {
        return client.call("POST", "/auth/password", Map.of("currentPassword", "A wrong current password", "newPassword", "A replacement test passphrase!"));
    }

    @Test
    void csrfWithAValidSessionIsNotLimited() throws Exception {
        var client = new AuthClient(port);
        assertEquals(200, client.csrf().statusCode());
        String email = email();
        assertEquals(201, client.register(email).statusCode());
        assertEquals(200, client.login(email, PASSWORD).statusCode());
        for (int i = 0; i < 10; i++) assertEquals(200, client.csrf().statusCode());
    }
}
