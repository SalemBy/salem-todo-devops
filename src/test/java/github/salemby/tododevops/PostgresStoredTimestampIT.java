package github.salemby.tododevops;

import github.salemby.tododevops.support.AuthClient;
import github.salemby.tododevops.support.CapturedVerificationMail;
import github.salemby.tododevops.support.IsolatedPostgresSchema;
import github.salemby.tododevops.support.TestHttp;
import github.salemby.tododevops.support.TestProjects;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Issue #63: a write response reports the instant PostgreSQL stores, not Java's finer clock.
 * An unfixed write matches by chance only when the clock lands on a whole microsecond, so each test writes four times.
 */
@SpringBootTest(classes = SalemTodoDevOpsApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("postgres-test")
@ContextConfiguration(initializers = IsolatedPostgresSchema.class)
class PostgresStoredTimestampIT {
    @Autowired Environment environment;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
    private final ObjectMapper json = new ObjectMapper();
    /** Its own account: the shared default one must stay free of projects for PostgresProjectApiIT's empty-list checks. */
    private TestHttp.Session owner;

    @Test
    void tasksReportStoredTimes() throws Exception {
        String base = "/projects/" + project() + "/tasks";
        var written = call("POST", base, "{\"title\":\"T0\"}", 201);
        String path = base + "/" + written.get("id").asLong();
        for (int i = 1; ; i++) {
            assertStored(written, call("GET", path, null, 200), "createdAt", "updatedAt");
            if (i > 3) break;
            written = changed(written, call("PUT", path, "{\"title\":\"T" + i + "\",\"status\":\"PENDING\"}", 200));
        }
    }

    @Test
    void projectsReportStoredTimes() throws Exception {
        var written = call("POST", "/projects", "{\"name\":\"P0\",\"key\":\"" + TestProjects.uniqueKey("TS") + "\"}", 201);
        String path = "/projects/" + written.get("id").asLong();
        for (int i = 1; ; i++) {
            assertStored(written, call("GET", path, null, 200), "createdAt", "updatedAt");
            if (i > 3) break;
            written = changed(written, call("PUT", path, "{\"name\":\"P" + i + "\"}", 200));
        }
    }

    @Test
    void labelsReportStoredTimes() throws Exception {
        String base = "/projects/" + project() + "/labels";
        var written = call("POST", base, "{\"name\":\"L0\"}", 201);
        String path = base + "/" + written.get("id").asLong();
        for (int i = 1; ; i++) {
            assertStored(written, call("GET", path, null, 200), "createdAt", "updatedAt");
            if (i > 3) break;
            written = changed(written, call("PUT", path, "{\"name\":\"L" + i + "\"}", 200));
        }
    }

    @Test
    void membersReportStoredTimes() throws Exception {
        var other = TestHttp.newSession(uri("/"));
        String base = "/projects/" + project() + "/members";
        var written = call("POST", base, "{\"email\":\"" + other.email() + "\",\"role\":\"MEMBER\"}", 201);
        String path = base + "/" + other.accountId();
        String[] roles = {"VIEWER", "MEMBER", "VIEWER"};
        for (int i = 0; ; i++) {
            assertStored(written, call("GET", path, null, 200), "joinedAt", "updatedAt");
            if (i == roles.length) break;
            written = changed(written, call("PUT", path, "{\"role\":\"" + roles[i] + "\"}", 200));
        }
    }

    @Test
    void accountsReportStoredTimes() throws Exception {
        int port = Integer.parseInt(environment.getRequiredProperty("local.server.port"));
        for (int i = 0; i < 4; i++) {
            var client = new AuthClient(port);
            client.csrf();
            // Unverified, so nothing changes the row between the response and the read (#71).
            String email = "stamp-" + UUID.randomUUID() + CapturedVerificationMail.UNVERIFIED_DOMAIN;
            var registered = client.register(email);
            assertEquals(201, registered.statusCode(), registered.body());
            var stored = jdbc.queryForMap("SELECT created_at, updated_at FROM accounts WHERE email = ?", email);
            var read = json.createObjectNode()
                .put("createdAt", instant(stored.get("created_at")))
                .put("updatedAt", instant(stored.get("updated_at")));
            assertStored(json.readTree(registered.body()), read, "createdAt", "updatedAt");
        }
    }

    private static String instant(Object timestamp) {
        return timestamp instanceof java.sql.Timestamp t ? t.toInstant().toString()
            : ((java.time.OffsetDateTime) timestamp).toInstant().toString();
    }

    /** The write response and the next read agree on every named timestamp. */
    static void assertStored(JsonNode written, JsonNode read, String... fields) {
        for (String f : fields) assertEquals(read.get(f).stringValue(), written.get(f).stringValue(), f + " as written vs as read");
    }

    /** Each update must move updatedAt, or a skipped @PreUpdate would pass unseen. */
    static JsonNode changed(JsonNode before, JsonNode after) {
        assertNotEquals(before.get("updatedAt"), after.get("updatedAt"), "the update moved updatedAt");
        return after;
    }

    private long project() throws Exception {
        return call("POST", "/projects", "{\"name\":\"Stamps\",\"key\":\"" + TestProjects.uniqueKey("TS") + "\"}", 201)
            .get("id").asLong();
    }

    private URI uri(String path) {
        return URI.create("http://127.0.0.1:" + environment.getProperty("local.server.port") + path);
    }

    private JsonNode call(String method, String path, String body, int status) throws Exception {
        if (owner == null) owner = TestHttp.newSession(uri("/"));
        var request = owner.request(path).header("Content-Type", "application/json")
            .method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body)).build();
        var response = TestHttp.client().send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(status, response.statusCode(), method + " " + path + ": " + response.body());
        return json.readTree(response.body());
    }
}
