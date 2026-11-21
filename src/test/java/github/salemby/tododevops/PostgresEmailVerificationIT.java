package github.salemby.tododevops;

import github.salemby.tododevops.support.AuthClient;
import github.salemby.tododevops.support.CapturedVerificationMail;
import github.salemby.tododevops.support.IsolatedPostgresSchema;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static github.salemby.tododevops.support.AuthClient.PASSWORD;
import static org.junit.jupiter.api.Assertions.*;

/** Issue #71: an account counts only after its owner proves they control the email address. */
@SpringBootTest(classes = SalemTodoDevOpsApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("postgres-test")
@ContextConfiguration(initializers = IsolatedPostgresSchema.class)
class PostgresEmailVerificationIT {
    static final String INVALID = "Verification link is invalid or has expired";

    @Autowired Environment environment;
    @Autowired JdbcTemplate jdbc;
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void registrationCreatesAnUnverifiedAccountAndOneEmail() throws Exception {
        var c = client();
        String email = unverifiedEmail();
        var registered = c.register(email);
        assertEquals(201, registered.statusCode(), registered.body());
        assertFalse(json.readTree(registered.body()).get("emailVerified").asBoolean());
        assertEquals(1, CapturedVerificationMail.sentTo(email).size());

        var unverified = c.login(email, PASSWORD);
        var wrongPassword = c.login(email, "not the password at all");
        assertEquals(401, unverified.statusCode());
        assertEquals(json.readTree(wrongPassword.body()).get("detail"), json.readTree(unverified.body()).get("detail"));
        assertEquals("Invalid credentials", json.readTree(unverified.body()).get("detail").stringValue());
    }

    @Test
    void verifyingAllowsLogin() throws Exception {
        var c = client();
        String email = unverifiedEmail();
        c.register(email);
        assertEquals(204, verify(c, CapturedVerificationMail.sentTo(email).get(0)).statusCode());
        var login = c.login(email, PASSWORD);
        assertEquals(200, login.statusCode(), login.body());
        assertTrue(json.readTree(login.body()).get("emailVerified").asBoolean());
    }

    @Test
    void aTokenWorksOnce() throws Exception {
        var c = client();
        String email = unverifiedEmail();
        c.register(email);
        String token = CapturedVerificationMail.sentTo(email).get(0);
        var first = CompletableFuture.supplyAsync(() -> call(() -> verify(c, token)));
        var second = CompletableFuture.supplyAsync(() -> call(() -> verify(c, token)));
        var statuses = List.of(first.get(), second.get()).stream().map(HttpResponse::statusCode).sorted().toList();
        assertEquals(List.of(204, 400), statuses);
        var refused = first.get().statusCode() == 400 ? first.get() : second.get();
        assertEquals(INVALID, json.readTree(refused.body()).get("detail").stringValue());
    }

    @Test
    void expiredAndUnknownTokensAreRejected() throws Exception {
        var c = client();
        String email = unverifiedEmail();
        c.register(email);
        jdbc.update("UPDATE accounts SET verification_token_expires_at = now() - interval '1 second' WHERE email = ?", email);
        assertDetail(verify(c, CapturedVerificationMail.sentTo(email).get(0)), 400, INVALID);
        assertDetail(verify(c, "nope"), 400, INVALID);
        for (Object body : List.of(Map.of("token", ""), Map.of(), Map.of("token", 5), Map.of("token", "x".repeat(101))))
            assertEquals(400, c.call("POST", "/auth/verify", body).statusCode(), String.valueOf(body));
        assertEquals(401, c.login(email, PASSWORD).statusCode(), "still unverified");
    }

    @Test
    void tokenIsStoredOnlyAsAHash() throws Exception {
        var c = client();
        String email = unverifiedEmail();
        var registered = c.register(email);
        String token = CapturedVerificationMail.sentTo(email).get(0);
        String stored = jdbc.queryForObject("SELECT verification_token_hash FROM accounts WHERE email = ?", String.class, email);
        assertEquals(sha256(token), stored);
        assertNotEquals(token, stored);
        var verified = verify(c, token);
        var login = c.login(email, PASSWORD);
        for (var response : List.of(registered, verified, login))
            assertFalse(response.body().contains(token), "a response leaked the token");
        assertNull(jdbc.queryForObject("SELECT verification_token_hash FROM accounts WHERE email = ?", String.class, email),
            "a used token is cleared");
    }

    @Test
    void existingFlowsStillWork() throws Exception {
        var c = client();
        String email = "ok-" + UUID.randomUUID() + "@example.com"; // the test mailer verifies these at once
        assertEquals(201, c.register(email).statusCode());
        assertEquals(200, c.login(email, PASSWORD).statusCode());
    }

    // --- fixtures -------------------------------------------------------------------------------

    private AuthClient client() throws Exception {
        var c = new AuthClient(Integer.parseInt(environment.getRequiredProperty("local.server.port")));
        c.csrf();
        return c;
    }

    static String unverifiedEmail() {
        return "v-" + UUID.randomUUID() + CapturedVerificationMail.UNVERIFIED_DOMAIN;
    }

    static HttpResponse<String> verify(AuthClient c, String token) throws Exception {
        return c.call("POST", "/auth/verify", Map.of("token", token));
    }

    private void assertDetail(HttpResponse<String> response, int status, String detail) throws Exception {
        assertEquals(status, response.statusCode(), response.body());
        JsonNode problem = json.readTree(response.body());
        assertEquals(detail, problem.get("detail").stringValue());
    }

    interface Call { HttpResponse<String> run() throws Exception; }

    static HttpResponse<String> call(Call call) {
        try {
            return call.run();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    static String sha256(String token) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
    }
}
