package github.salemby.tododevops;

import github.salemby.tododevops.identity.infrastructure.ratelimit.RateLimitProperties.Limit;
import github.salemby.tododevops.identity.infrastructure.ratelimit.RedisRateLimiter;
import github.salemby.tododevops.support.AuthClient;
import github.salemby.tododevops.support.IsolatedPostgresSchema;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;

import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;

import static github.salemby.tododevops.support.AuthClient.PASSWORD;
import static org.junit.jupiter.api.Assertions.*;

/** Per-email login and per-login password-change limits. Address, registration and session limits stay high (test profile). */
@SpringBootTest(classes = SalemTodoDevOpsApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("postgres-test")
@ContextConfiguration(initializers = IsolatedPostgresSchema.class)
@TestPropertySource(properties = {"app.rate-limit.login-per-email.max=3", "app.rate-limit.login-per-email.window=60s",
    "app.rate-limit.verification-email-per-email.max=2", "app.rate-limit.verification-email-per-email.window=60s",
    "app.rate-limit.password-change-per-login.max=3", "app.rate-limit.password-change-per-login.window=60s"})
class PostgresAuthRateLimitIT {
    @Autowired RedisRateLimiter limiter;
    @Autowired JdbcTemplate jdbc;
    @Value("${local.server.port}") int port;
    final List<String> emails = new ArrayList<>();

    @AfterEach void cleanup() { for (String email : emails) jdbc.update("delete from accounts where email = ?", email); }

    String email() { String value = UUID.randomUUID() + "@example.com"; emails.add(value); return value; }

    AuthClient client() throws Exception { var client = new AuthClient(port); assertEquals(200, client.csrf().statusCode()); return client; }

    @Test
    void limiterRejectsTheHitAfterMaxWithARetryAfter() {
        var limit = new Limit(3, Duration.ofSeconds(60));
        String key = "k1";
        for (int i = 0; i < 3; i++) assertEquals(Optional.empty(), limiter.hit(key, limit));
        var retryAfter = limiter.hit(key, limit).orElseThrow();
        assertTrue(retryAfter.compareTo(Duration.ofSeconds(59)) >= 0 && retryAfter.compareTo(Duration.ofSeconds(60)) <= 0,
            "Retry-After was " + retryAfter);
    }

    @Test
    void resetStartsANewWindow() {
        var limit = new Limit(3, Duration.ofSeconds(60));
        String key = "k2";
        for (int i = 0; i < 4; i++) limiter.hit(key, limit);
        limiter.reset(key);
        assertEquals(Optional.empty(), limiter.hit(key, limit));
    }

    @Test
    void windowExpiryLiftsTheLimit() throws Exception {
        // Limiter level, so only two Redis round trips must fit in the 1s window. The email limit uses the same hit.
        var limit = new Limit(1, Duration.ofSeconds(1));
        String key = "k3";
        assertEquals(Optional.empty(), limiter.hit(key, limit));
        assertTrue(limiter.hit(key, limit).isPresent());
        Thread.sleep(1200);
        assertEquals(Optional.empty(), limiter.hit(key, limit));
    }

    @Test
    void emailLimitReturns429WithRetryAfterForExistingAndUnknownEmails() throws Exception {
        var client = client();
        String existing = email();
        assertEquals(201, client.register(existing).statusCode());
        var forExisting = throttled(client, existing);
        var forUnknown = throttled(client, UUID.randomUUID() + "@example.com");
        assertEquals(forExisting.body(), forUnknown.body(), "429 must not reveal whether the email exists");
    }

    @Test
    void emailLimitBlocksEvenTheCorrectPassword() throws Exception {
        var client = client();
        String email = email();
        assertEquals(201, client.register(email).statusCode());
        throttled(client, email);
        assertEquals(429, client.login(email, PASSWORD).statusCode());
    }

    @Test
    void successfulLoginResetsTheEmailCount() throws Exception {
        var client = client();
        String email = email();
        assertEquals(201, client.register(email).statusCode());
        for (int i = 0; i < 2; i++) assertEquals(401, client.login(email, "wrong password!").statusCode());
        assertEquals(200, client.login(email, PASSWORD).statusCode());
        var again = client();
        for (int i = 0; i < 3; i++) assertEquals(401, again.login(email, "wrong password!").statusCode());
    }

    @Test
    void concurrentAttemptsNeverExceedTheEmailLimit() throws Exception {
        var client = client();
        String email = UUID.randomUUID() + "@example.com";
        List<Callable<Integer>> attempts = new ArrayList<>();
        for (int i = 0; i < 10; i++) attempts.add(() -> client.login(email, "wrong password!").statusCode());
        var pool = Executors.newFixedThreadPool(10);
        var statuses = new ArrayList<Integer>();
        try {
            for (var result : pool.invokeAll(attempts)) statuses.add(result.get());
        } finally {
            pool.shutdownNow();
        }
        assertEquals(3, statuses.stream().filter(s -> s == 401).count(), statuses.toString());
        assertEquals(7, statuses.stream().filter(s -> s == 429).count(), statuses.toString());
    }

    @Test
    void passwordChangeLimitReturns429EvenForTheCorrectPassword() throws Exception {
        String email = email();
        var client = loggedIn(email);
        spendPasswordChanges(client);
        assertThrottled(changePassword(client, PASSWORD));
        assertEquals(200, client().login(email, PASSWORD).statusCode(), "the throttled attempt must not change the password");
    }

    @Test
    void passwordChangeLimitIsPerLogin() throws Exception {
        String email = email();
        var stolen = loggedIn(email);
        spendPasswordChanges(stolen);
        assertEquals(429, changePassword(stolen, PASSWORD).statusCode());
        var owner = client();
        assertEquals(200, owner.login(email, PASSWORD).statusCode());
        owner.csrf();
        assertEquals(204, changePassword(owner, PASSWORD).statusCode(), "the owner's own login keeps its attempts");
        assertEquals(401, stolen.call("GET", "/auth/me", null).statusCode(), "the change revokes the throttled session");
    }

    AuthClient loggedIn(String email) throws Exception {
        var client = client();
        assertEquals(201, client.register(email).statusCode());
        assertEquals(200, client.login(email, PASSWORD).statusCode());
        client.csrf();
        return client;
    }

    static HttpResponse<String> changePassword(AuthClient client, String current) throws Exception {
        return client.call("POST", "/auth/password", Map.of("currentPassword", current, "newPassword", "A replacement test passphrase!"));
    }

    @Test
    void verificationEmailLimitIsPerEmailWhetherOrNotTheAccountExists() throws Exception {
        var client = new AuthClient(port);
        assertEquals(200, client.csrf().statusCode());
        String limited = UUID.randomUUID() + "@unverified.example.com";
        for (int i = 0; i < 2; i++)
            assertEquals(202, client.call("POST", "/auth/verification-email", java.util.Map.of("email", limited)).statusCode());
        assertThrottled(client.call("POST", "/auth/verification-email", java.util.Map.of("email", limited)));
        var other = client.call("POST", "/auth/verification-email", java.util.Map.of("email", UUID.randomUUID() + "@example.com"));
        assertEquals(202, other.statusCode(), "another email has its own count");
    }

    static void spendPasswordChanges(AuthClient client) throws Exception {
        for (int i = 0; i < 3; i++) assertEquals(400, changePassword(client, "A wrong current password").statusCode());
    }

    /** Uses up the email's attempts and returns the first throttled response, checking its contract. */
    private HttpResponse<String> throttled(AuthClient client, String email) throws Exception {
        for (int i = 0; i < 3; i++) assertEquals(401, client.login(email, "wrong password!").statusCode());
        return assertThrottled(client.login(email, "wrong password!"));
    }

    private static HttpResponse<String> assertThrottled(HttpResponse<String> response) {
        assertEquals(429, response.statusCode());
        assertEquals("application/problem+json", response.headers().firstValue("Content-Type").orElseThrow());
        assertTrue(response.headers().firstValue("Cache-Control").orElseThrow().contains("no-store"));
        long retryAfter = Long.parseLong(response.headers().firstValue("Retry-After").orElseThrow());
        assertTrue(retryAfter >= 1 && retryAfter <= 60, "Retry-After was " + retryAfter);
        assertTrue(response.body().contains("\"detail\":\"Too many requests. Try again later.\""), response.body());
        return response;
    }
}
