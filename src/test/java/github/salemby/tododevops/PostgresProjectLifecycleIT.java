package github.salemby.tododevops;

import github.salemby.tododevops.support.IsolatedPostgresSchema;
import github.salemby.tododevops.support.TestHttp;
import github.salemby.tododevops.workmanagement.infrastructure.persistence.ProjectRepository;
import github.salemby.tododevops.workmanagement.infrastructure.persistence.TaskRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import javax.sql.DataSource;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = SalemTodoDevOpsApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("postgres-test")
@ContextConfiguration(initializers = IsolatedPostgresSchema.class)
class PostgresProjectLifecycleIT {
    @Autowired Environment environment;
    @Autowired DataSource dataSource;
    @Autowired ProjectRepository projects;
    @Autowired TaskRepository tasks;
    private final ObjectMapper json = new ObjectMapper();
    private long projectId;
    private JsonNode original;
    private JsonNode backlog;
    private static final String UPDATE = "{\"name\":\"Concurrent update\",\"description\":\"Changed details\"}";

    @BeforeEach
    void createHistory() throws Exception {
        var response = request("POST", "/projects", "{\"name\":\"Original\",\"description\":\"Retained details\",\"key\":\"P"
            + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase() + "\"}");
        assertEquals(201, response.statusCode(), response.body());
        original = json.readTree(response.body());
        projectId = original.get("id").asLong();
        var active = request("POST", path() + "/tasks", "{\"title\":\"Retained active task\"}");
        assertEquals(201, active.statusCode(), active.body());
        var inactive = request("POST", path() + "/tasks", "{\"title\":\"Retained inactive task\"}");
        assertEquals(201, inactive.statusCode(), inactive.body());
        assertEquals(204, request("DELETE", path() + "/tasks/" + json.readTree(inactive.body()).get("id").asLong(), null).statusCode());
        // Read persisted precision: PostgreSQL rounds creation timestamps to microseconds.
        original = read(path());
        backlog = read(path() + "/tasks?active=all");
    }

    @AfterEach
    void cleanup() {
        if (projectId != 0) {
            tasks.deleteAll(tasks.findAllByProjectId(projectId));
            projects.deleteById(projectId);
        }
    }

    @Test
    void archiveWinningAgainstUpdateRejectsStaleDetailsAndPreservesHistory() throws Exception {
        var responses = contend("DELETE", path(), null, "PUT", path(), UPDATE);
        assertEquals(204, responses.first().statusCode(), responses.first().body());
        assertProblem(responses.second(), 404);
        var archived = assertArchivedHistory();
        assertEquals(original.get("name"), archived.get("name"));
        assertEquals(original.get("description"), archived.get("description"));
        // Further rejected writes and repeated archive must preserve every field and timestamp.
        assertProblem(request("PUT", path(), UPDATE), 404);
        assertEquals(204, request("DELETE", path(), null).statusCode());
        assertEquals(archived, read(path()));
    }

    @Test
    void updateWinningAgainstArchiveRetainsUpdatedDetailsButEndsArchived() throws Exception {
        var responses = contend("PUT", path(), UPDATE, "DELETE", path(), null);
        assertEquals(200, responses.first().statusCode(), responses.first().body());
        assertTrue(json.readTree(responses.first().body()).get("active").asBoolean());
        assertEquals(204, responses.second().statusCode(), responses.second().body());
        var archived = assertArchivedHistory();
        assertEquals("Concurrent update", archived.get("name").stringValue());
        assertEquals("Changed details", archived.get("description").stringValue());
    }

    @Test
    void archiveWinningAgainstTaskCreationRejectsInsertionAndPreservesHistory() throws Exception {
        var responses = contend("DELETE", path(), null, "POST", path() + "/tasks", "{\"title\":\"Blocked task\"}");
        assertEquals(204, responses.first().statusCode(), responses.first().body());
        assertProblem(responses.second(), 409);
        assertArchivedHistory();
        assertEquals(2, tasks.findAllByProjectId(projectId).size());
    }

    private JsonNode assertArchivedHistory() throws Exception {
        var archived = read(path());
        assertFalse(archived.get("active").asBoolean());
        assertFalse(projects.findById(projectId).orElseThrow().isActive(), "Persisted project must remain archived");
        assertEquals(original.get("key"), archived.get("key"));
        assertEquals(original.get("createdAt"), archived.get("createdAt"));
        // Compare by task ID rather than depending on unspecified backlog ordering.
        var retained = read(path() + "/tasks?active=all");
        assertEquals(backlog.size(), retained.size());
        for (var before : backlog) {
            var after = read(path() + "/tasks/" + before.get("id").asLong());
            assertEquals(before, after, "Archiving must preserve active and inactive task history");
            assertTrue(java.util.stream.StreamSupport.stream(retained.spliterator(), false).anyMatch(before::equals));
        }
        return archived;
    }

    private record Responses(HttpResponse<String> first, HttpResponse<String> second) {}

    private Responses contend(String firstMethod, String firstPath, String firstBody,
                              String secondMethod, String secondPath, String secondBody) throws Exception {
        var workers = Executors.newFixedThreadPool(2);
        try (var blocker = dataSource.getConnection()) {
            blocker.setAutoCommit(false);
            try {
                try (var statement = blocker.prepareStatement("SELECT id FROM projects WHERE id = ? FOR UPDATE")) {
                    statement.setQueryTimeout(3);
                    statement.setLong(1, projectId);
                    try (var row = statement.executeQuery()) { assertTrue(row.next()); }
                }
                var first = workers.submit(() -> request(firstMethod, firstPath, firstBody));
                awaitBlockedWrites(blocker, 1);
                var second = workers.submit(() -> request(secondMethod, secondPath, secondBody));
                awaitBlockedWrites(blocker, 2);
                assertFalse(first.isDone());
                assertFalse(second.isDone());
                blocker.commit();
                return new Responses(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS));
            } finally {
                blocker.rollback();
            }
        } finally {
            workers.shutdownNow();
            assertTrue(workers.awaitTermination(20, TimeUnit.SECONDS), "HTTP workers did not terminate");
        }
    }

    private void awaitBlockedWrites(Connection blocker, int expected) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        try (var statement = blocker.createStatement()) {
            statement.setQueryTimeout(2);
            while (System.nanoTime() < deadline) {
                statement.execute("SELECT pg_stat_clear_snapshot()");
                // Follow indirect blockers too: the second writer may wait behind the first writer.
                try (var rows = statement.executeQuery("""
                        WITH RECURSIVE blocked(pid) AS (
                            SELECT pg_backend_pid()
                            UNION
                            SELECT activity.pid FROM pg_stat_activity activity
                            JOIN blocked parent ON parent.pid = ANY(pg_blocking_pids(activity.pid))
                        )
                        SELECT count(*) FROM pg_stat_activity
                        WHERE pid IN (SELECT pid FROM blocked)
                          AND pid <> pg_backend_pid() AND wait_event_type = 'Lock'
                        """)) {
                    assertTrue(rows.next());
                    if (rows.getInt(1) == expected) return;
                }
                Thread.sleep(25);
            }
        }
        fail("Expected " + expected + " HTTP writes waiting behind this project's row lock");
    }

    private String path() { return "/projects/" + projectId; }

    private JsonNode read(String path) throws Exception {
        var response = request("GET", path, null);
        assertEquals(200, response.statusCode(), response.body());
        return json.readTree(response.body());
    }

    private void assertProblem(HttpResponse<String> response, int status) {
        assertEquals(status, response.statusCode(), response.body());
        assertTrue(response.headers().firstValue("Content-Type").orElseThrow().startsWith("application/problem+json"));
        var problem = json.readTree(response.body());
        assertEquals(status, problem.get("status").asInt());
        assertEquals("urn:projetotodo:problem:" + status, problem.get("type").stringValue());
        assertTrue(problem.get("errors").isArray());
    }

    private HttpResponse<String> request(String method, String path, String body) throws Exception {
        var builder = TestHttp.request(URI.create("http://127.0.0.1:" + environment.getProperty("local.server.port") + path));
        if (body != null) builder.header("Content-Type", "application/json");
        return TestHttp.client().send(builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
            : HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }
}
