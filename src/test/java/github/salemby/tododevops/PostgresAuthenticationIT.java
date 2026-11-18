package github.salemby.tododevops;

import github.salemby.tododevops.support.IsolatedPostgresSchema;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import java.net.*;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import tools.jackson.databind.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = SalemTodoDevOpsApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("postgres-test")
@ContextConfiguration(initializers = IsolatedPostgresSchema.class)
@org.springframework.context.annotation.Import(PostgresAuthenticationIT.ExpiryConfiguration.class)
class PostgresAuthenticationIT {
    @Autowired Environment environment;
    @Autowired JdbcTemplate jdbc;
    final ObjectMapper json = new ObjectMapper();
    final List<String> emails = new ArrayList<>();
    static final String PASSWORD = "A test only passphrase!";
    @org.springframework.boot.test.context.TestConfiguration(proxyBeanMethods = false)
    static class ExpiryConfiguration {
        @org.springframework.context.annotation.Bean ExpiryEndpoint expiryEndpoint() { return new ExpiryEndpoint(); }
    }
    @org.springframework.web.bind.annotation.RestController
    static class ExpiryEndpoint {
        @org.springframework.web.bind.annotation.PostMapping("/test-only/short-session")
        void shorten(jakarta.servlet.http.HttpServletRequest request) { request.getSession(false).setMaxInactiveInterval(1); }
    }
    @AfterEach void cleanup() { for (String email : emails) jdbc.update("delete from accounts where email = ?", email); }
    String email() { String value = UUID.randomUUID() + "@example.com"; emails.add(value); return value; }
    Client client() { return new Client(); }

    final class Client {
        final CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        final HttpClient http = HttpClient.newBuilder().cookieHandler(cookies).connectTimeout(Duration.ofSeconds(5)).build();
        String token, header;
        HttpResponse<String> call(String method, String path, Object body, boolean csrf) throws Exception {
            var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + environment.getProperty("local.server.port") + path))
                .timeout(Duration.ofSeconds(15)).header("Content-Type", "application/json");
            if (csrf && token != null) request.header(header, token);
            return http.send(request.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(body instanceof String s ? s : json.writeValueAsString(body))).build(), HttpResponse.BodyHandlers.ofString());
        }
        void csrf() throws Exception {
            var response = call("GET", "/auth/csrf", null, false);
            assertEquals(200, response.statusCode());
            assertTrue(response.headers().firstValue("Cache-Control").orElseThrow().contains("no-store"));
            var body = json.readTree(response.body()); token = body.get("token").asText(); header = body.get("headerName").asText();
        }
        HttpResponse<String> register(String email) throws Exception { return call("POST", "/auth/register", Map.of("email", email, "displayName", "Person", "password", PASSWORD), true); }
        HttpResponse<String> login(String email, String password) throws Exception { return call("POST", "/auth/login", Map.of("email", email, "password", password), true); }
        String sessionId() { return cookies.getCookieStore().getCookies().stream().filter(c -> c.getName().equals("JSESSIONID")).map(HttpCookie::getValue).findFirst().orElseThrow(); }
    }
    Client registered(String email) throws Exception { var c = client(); c.csrf(); assertEquals(201, c.register(email).statusCode()); return c; }
    Client loggedIn(String email) throws Exception {
        var c = registered(email); assertEquals(200, c.login(email, PASSWORD).statusCode()); c.csrf(); return c;
    }
    void problem(HttpResponse<String> response, int status, String path) throws Exception {
        assertEquals(status, response.statusCode(), response.body());
        assertTrue(response.headers().firstValue("Content-Type").orElseThrow().startsWith("application/problem+json"));
        var body = json.readTree(response.body());
        assertEquals(status, body.get("status").asInt());
        assertEquals("urn:projetotodo:problem:" + status, body.get("type").asText());
        assertEquals(path, body.get("instance").asText()); assertTrue(body.get("errors").isArray());
        assertFalse(response.body().contains(PASSWORD)); assertFalse(response.body().contains("passwordHash"));
    }

    @Test void registrationDoesNotLoginAndLoginRotatesSessionAndCsrf() throws Exception {
        String email = email(); var c = registered(email);
        problem(c.call("GET", "/auth/me", null, false), 401, "/auth/me");
        String anonymousId = c.sessionId(), previousToken = c.token;
        var login = c.login(" " + email.toUpperCase(Locale.ROOT) + " ", PASSWORD);
        assertEquals(200, login.statusCode(), login.body());
        assertNotEquals(anonymousId, c.sessionId());
        String authenticatedId = c.sessionId();
        assertEquals(email, json.readTree(login.body()).get("email").asText());
        assertFalse(login.body().contains("password")); assertFalse(login.body().contains("credentialVersion"));
        assertTrue(login.headers().allValues("Set-Cookie").stream().anyMatch(s -> s.contains("HttpOnly") && s.contains("SameSite=Lax") && s.contains("Path=/")));
        assertTrue(login.headers().allValues("Set-Cookie").stream().noneMatch(s -> s.toLowerCase().contains("domain=")));
        assertEquals(200, c.call("GET", "/auth/me", null, false).statusCode());
        assertEquals(200, c.call("GET", "/projects", null, false).statusCode());
        problem(c.call("POST", "/auth/logout", null, true), 403, "/auth/logout");
        c.csrf(); assertNotEquals(previousToken, c.token);
        var logout = c.call("POST", "/auth/logout", null, true);
        assertEquals(204, logout.statusCode());
        assertTrue(logout.headers().allValues("Set-Cookie").stream().anyMatch(s -> s.contains("Max-Age=0")));
        problem(c.call("GET", "/auth/me", null, false), 401, "/auth/me");
        // A copied old session cookie cannot resurrect the authenticated identity.
        var replay = client(); replay.cookies.getCookieStore().add(URI.create("http://127.0.0.1"), new HttpCookie("JSESSIONID", authenticatedId));
        problem(replay.call("GET", "/auth/me", null, false), 401, "/auth/me");
    }

    @Test void authenticationPrecedesCsrfOnProtectedRoutesAndPublicWritesRequireCsrf() throws Exception {
        var c = client();
        for (String path : List.of("/projects", "/auth/me", "/unlisted-route", "/error")) problem(c.call("GET", path, null, false), 401, path);
        for (String path : List.of("/projects", "/auth/logout", "/auth/password")) problem(c.call("POST", path, "{}", false), 401, path);
        for (String path : List.of("/auth/register", "/auth/login")) problem(c.call("POST", path, "{}", false), 403, path);
        assertEquals(200, c.call("GET", "/", null, false).statusCode());
        var authenticated = loggedIn(email());
        problem(authenticated.call("POST", "/projects", "{}", false), 403, "/projects");
        problem(authenticated.call("POST", "/projects", "{}", true), 400, "/projects");
    }

    @Test void loginFailuresAreGenericAndDisabledSessionsAreRevoked() throws Exception {
        String email = email(); var c = loggedIn(email()); // independent signed-in identity
        var target = registered(email);
        var wrong = target.login(email, "wrong password");
        var absent = target.login(email(), PASSWORD);
        jdbc.update("update accounts set active = false where email = ?", email);
        var disabled = target.login(email, PASSWORD);
        for (var r : List.of(wrong, absent, disabled)) problem(r, 401, "/auth/login");
        assertEquals(wrong.body(), absent.body()); assertEquals(wrong.body(), disabled.body());
        var current = json.readTree(c.call("GET", "/auth/me", null, false).body());
        jdbc.update("update accounts set active = false where id = ?", current.get("id").asLong());
        problem(c.call("POST", "/projects", "{}", false), 401, "/projects");
    }

    @Test void rejectsMalformedAndInvalidInputWithoutLeakingSecrets() throws Exception {
        var c = client(); c.csrf();
        problem(c.call("POST", "/auth/login", "{broken", true), 400, "/auth/login");
        problem(c.call("POST", "/auth/register", Map.of("email", "invalid", "displayName", "", "password", "short"), true), 400, "/auth/register");
        problem(c.call("POST", "/auth/login", Map.of("email", "a@example.com", "password", "x".repeat(257)), true), 400, "/auth/login");
    }

    @Test void duplicateRegistrationIsCaseInsensitiveIncludingConcurrentRequests() throws Exception {
        String email = email(); var a = client(); a.csrf(); var b = client(); b.csrf();
        var pool = Executors.newFixedThreadPool(2);
        try {
            var start = new CountDownLatch(1);
            var first = pool.submit(() -> { start.await(); return a.register(email); });
            var second = pool.submit(() -> { start.await(); return b.register(" " + email.toUpperCase(Locale.ROOT) + " "); });
            start.countDown();
            var responses = List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS));
            assertEquals(List.of(201, 409), responses.stream().map(HttpResponse::statusCode).sorted().toList());
            problem(responses.stream().filter(r -> r.statusCode() == 409).findFirst().orElseThrow(), 409, "/auth/register");
            assertEquals(1, jdbc.queryForObject("select count(*) from accounts where email = ?", Integer.class, email));
        } finally { pool.shutdownNow(); }
    }

    @Test void passwordChangeRevokesBothClientsAndRejectsOldPassword() throws Exception {
        String email = email(); var a = loggedIn(email); var b = client(); b.csrf();
        assertEquals(200, b.login(email, PASSWORD).statusCode()); b.csrf();
        problem(a.call("POST", "/auth/password", Map.of("currentPassword", "incorrect", "newPassword", "A replacement password!"), true), 400, "/auth/password");
        problem(a.call("POST", "/auth/password", Map.of("currentPassword", PASSWORD, "newPassword", "short"), true), 400, "/auth/password");
        assertEquals(200, b.call("GET", "/auth/me", null, false).statusCode());
        assertEquals(204, a.call("POST", "/auth/password", Map.of("currentPassword", PASSWORD, "newPassword", "A replacement password!"), true).statusCode());
        problem(a.call("GET", "/auth/me", null, false), 401, "/auth/me");
        problem(b.call("POST", "/projects", "{}", true), 401, "/projects");
        b.csrf(); problem(b.login(email, PASSWORD), 401, "/auth/login");
        assertEquals(200, b.login(email, "A replacement password!").statusCode());
        assertEquals(1L, jdbc.queryForObject("select credential_version from accounts where email = ?", Long.class, email));
    }

    @Test void idleExpirationRejectsPreviouslyAuthenticatedCookie() throws Exception {
        var c = loggedIn(email());
        assertEquals(200, c.call("POST", "/test-only/short-session", null, true).statusCode());
        Thread.sleep(2100); // Exercise actual servlet-container expiry, not a mocked security context.
        problem(c.call("GET", "/auth/me", null, false), 401, "/auth/me");
        problem(c.call("POST", "/projects", "{}", true), 401, "/projects");
    }

    @Test void concurrentPasswordChangesCannotLoseRevocationOrOverwriteWinner() throws Exception {
        String email = email(); var a = loggedIn(email); var b = client(); b.csrf();
        assertEquals(200, b.login(email, PASSWORD).statusCode()); b.csrf();
        var pool = Executors.newFixedThreadPool(2); var start = new CountDownLatch(1);
        try {
            var first = pool.submit(() -> { start.await(); return a.call("POST", "/auth/password", Map.of("currentPassword", PASSWORD, "newPassword", "First replacement password!"), true); });
            var second = pool.submit(() -> { start.await(); return b.call("POST", "/auth/password", Map.of("currentPassword", PASSWORD, "newPassword", "Second replacement password!"), true); });
            start.countDown(); var firstResult = first.get(20, TimeUnit.SECONDS); var secondResult = second.get(20, TimeUnit.SECONDS);
            assertEquals(List.of(204, 401), java.util.stream.Stream.of(firstResult, secondResult).map(HttpResponse::statusCode).sorted().toList());
            assertEquals(1L, jdbc.queryForObject("select credential_version from accounts where email = ?", Long.class, email));
            var c = client(); c.csrf();
            assertEquals(200, c.login(email, firstResult.statusCode() == 204 ? "First replacement password!" : "Second replacement password!").statusCode());
            problem(a.call("GET", "/auth/me", null, false), 401, "/auth/me");
            problem(b.call("GET", "/auth/me", null, false), 401, "/auth/me");
        } finally { pool.shutdownNow(); }
    }
}
