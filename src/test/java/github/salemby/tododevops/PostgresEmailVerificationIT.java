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
    @Autowired github.salemby.tododevops.identity.application.EmailVerificationService verification;
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

    @Test
    void sendingAgainReplacesTheToken() throws Exception {
        var c = client();
        String email = unverifiedEmail();
        c.register(email);
        var again = sendAgain(c, email);
        assertEquals(202, again.statusCode(), again.body());
        assertEquals("", again.body());
        var sent = CapturedVerificationMail.sentTo(email);
        assertEquals(2, sent.size());
        assertDetail(verify(c, sent.get(0)), 400, INVALID);
        assertEquals(204, verify(c, sent.get(1)).statusCode());
        assertEquals(200, c.login(email, PASSWORD).statusCode());
    }

    @Test
    void sendAgainLooksTheSameForEveryEmail() throws Exception {
        var c = client();
        String unknown = unverifiedEmail(), pending = unverifiedEmail(), verified = "done-" + UUID.randomUUID() + "@example.com";
        c.register(pending);
        c.register(verified); // verified at once by the test mailer
        var responses = new java.util.ArrayList<HttpResponse<String>>();
        for (String email : List.of(unknown, pending, verified)) responses.add(sendAgain(c, email));
        for (var response : responses) {
            assertEquals(202, response.statusCode(), response.body());
            assertEquals("", response.body());
            assertEquals(headers(responses.get(0)), headers(response));
        }
        assertEquals(0, CapturedVerificationMail.sentTo(unknown).size());
        assertEquals(2, CapturedVerificationMail.sentTo(pending).size());
        assertEquals(1, CapturedVerificationMail.sentTo(verified).size(), "a verified account gets no new email");
        for (Object body : List.of(Map.of(), Map.of("email", "not an email"), Map.of("email", 5)))
            assertEquals(400, c.call("POST", "/auth/verification-email", body).statusCode(), String.valueOf(body));
    }

    @Test
    void reRegistrationReplacesOnlyPendingAccounts() throws Exception {
        var c = client();
        String email = unverifiedEmail();
        assertEquals(201, c.call("POST", "/auth/register", Map.of("email", email, "displayName", "First", "password", PASSWORD)).statusCode());
        String second = "A different passphrase entirely";
        var replaced = c.call("POST", "/auth/register", Map.of("email", email, "displayName", "Second", "password", second));
        assertEquals(201, replaced.statusCode(), replaced.body());
        var sent = CapturedVerificationMail.sentTo(email);
        assertEquals(2, sent.size());
        assertDetail(verify(c, sent.get(0)), 400, INVALID);
        assertEquals(204, verify(c, sent.get(1)).statusCode());
        assertEquals(401, c.login(email, PASSWORD).statusCode(), "the replaced password no longer works");
        var login = c.login(email, second);
        assertEquals(200, login.statusCode(), login.body());
        assertEquals("Second", json.readTree(login.body()).get("displayName").stringValue());
        c.csrf(); // login rotates the token
        var duplicate = c.call("POST", "/auth/register", Map.of("email", email, "displayName", "Third", "password", second));
        assertDetail(duplicate, 409, "Email is already registered");
    }

    @Test
    void unverifiedAccountsCannotBeAddedToProjects() throws Exception {
        var owner = client();
        String ownerEmail = "owner-" + UUID.randomUUID() + "@example.com";
        owner.register(ownerEmail);
        assertEquals(200, owner.login(ownerEmail, PASSWORD).statusCode());
        owner.csrf();
        var project = owner.call("POST", "/projects", Map.of("name", "Verified only", "key", "V" + UUID.randomUUID().toString()
            .replace("-", "").substring(0, 8).toUpperCase()));
        assertEquals(201, project.statusCode(), project.body());
        long p = json.readTree(project.body()).get("id").asLong();
        String pending = unverifiedEmail();
        client().register(pending);
        assertDetail(owner.call("POST", "/projects/" + p + "/members", Map.of("email", pending, "role", "MEMBER")), 422,
            "Account not found");
    }

    @Test
    void staleUnverifiedAccountsAreDeleted() throws Exception {
        var c = client();
        String stale = unverifiedEmail(), recent = unverifiedEmail(), old = "old-" + UUID.randomUUID() + "@example.com";
        for (String email : List.of(stale, recent, old)) c.register(email);
        jdbc.update("UPDATE accounts SET updated_at = now() - interval '8 days' WHERE email = ?", stale);
        jdbc.update("UPDATE accounts SET created_at = now() - interval '30 days', updated_at = now() - interval '30 days' WHERE email = ?", old);
        assertEquals(1, verification.deleteStaleUnverified(java.time.Instant.now()));
        assertEquals(List.of(recent, old), jdbc.queryForList(
            "SELECT email FROM accounts WHERE email IN (?, ?, ?) ORDER BY email = ? DESC", String.class, stale, recent, old, recent));
        assertEquals(201, c.register(stale).statusCode(), "the email is free again");
    }

    // --- fixtures -------------------------------------------------------------------------------

    static HttpResponse<String> sendAgain(AuthClient c, String email) throws Exception {
        return c.call("POST", "/auth/verification-email", Map.of("email", email));
    }

    static Map<String, List<String>> headers(HttpResponse<String> response) {
        var all = new java.util.TreeMap<>(response.headers().map());
        all.remove("date");
        all.remove("set-cookie");
        return all;
    }

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
