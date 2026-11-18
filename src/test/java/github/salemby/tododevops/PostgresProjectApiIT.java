package github.salemby.tododevops;

import github.salemby.tododevops.support.TestHttp;
import github.salemby.tododevops.support.IsolatedPostgresSchema;
import org.springframework.test.context.ContextConfiguration;

import github.salemby.tododevops.workmanagement.infrastructure.persistence.ProjectRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.sql.Connection;
import javax.sql.DataSource;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = SalemTodoDevOpsApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@org.springframework.test.context.ActiveProfiles("postgres-test")
@ContextConfiguration(initializers = IsolatedPostgresSchema.class)
class PostgresProjectApiIT {
    @Autowired Environment environment;
    @Autowired ProjectRepository projects;
    @Autowired DataSource dataSource;
    private final ObjectMapper json = new ObjectMapper();
    private final HttpClient client = TestHttp.client();
    private final Set<Long> createdIds = ConcurrentHashMap.newKeySet();

    @AfterEach
    void removeCreatedProjects() {
        projects.deleteAllById(createdIds);
    }

    @Test
    void createsReadsAndUpdatesProjectDetails() throws Exception {
        var response = create("Todo platform", "TODO", "Original details");
        assertEquals(201, response.statusCode(), response.body());
        var project = json.readTree(response.body());
        long id = project.get("id").asLong();
        assertTrue(response.headers().firstValue("Location").orElseThrow().endsWith("/projects/" + id));
        assertEquals("Todo platform", project.get("name").stringValue());
        assertEquals("TODO", project.get("key").stringValue());
        assertEquals("Original details", project.get("description").stringValue());
        assertTrue(project.get("active").asBoolean());
        var createdAt = Instant.parse(project.get("createdAt").stringValue());
        assertEquals(createdAt, Instant.parse(project.get("updatedAt").stringValue()));
        var original = withHistoricalTimestamps(id);
        var read = request("GET", "/projects/" + id, null);
        assertEquals(200, read.statusCode());
        assertEquals("TODO", json.readTree(read.body()).get("key").stringValue());

        var updatedResponse = request("PUT", "/projects/" + id,
            "{\"name\":\"Updated\",\"description\":\"New details\",\"key\":\"HACK\","
                + "\"active\":false,\"id\":999,\"createdAt\":\"1990-01-01T00:00:00Z\"}");
        assertEquals(200, updatedResponse.statusCode(), updatedResponse.body());
        var updated = projects.findById(id).orElseThrow();
        assertEquals("Updated", updated.getName());
        assertEquals("New details", updated.getDescription());
        assertEquals("TODO", updated.getKey());
        assertTrue(updated.isActive());
        assertEquals(original.getCreatedAt(), updated.getCreatedAt());
        assertTrue(updated.getUpdatedAt().isAfter(original.getUpdatedAt()));
        var updatedJson = json.readTree(updatedResponse.body());
        assertEquals(id, updatedJson.get("id").asLong());
        assertEquals("TODO", updatedJson.get("key").stringValue());
        assertTrue(java.time.Duration.between(updated.getUpdatedAt(),
            Instant.parse(updatedJson.get("updatedAt").stringValue())).abs().toNanos() < 1000);

        assertEquals(200, request("PUT", "/projects/" + id, "{\"name\":\"No description\"}").statusCode());
        assertNull(projects.findById(id).orElseThrow().getDescription());
        assertEquals(200, request("PUT", "/projects/" + id,
            "{\"name\":\"Null description\",\"description\":null}").statusCode());
        assertNull(projects.findById(id).orElseThrow().getDescription());
    }

    @Test
    void creatorBecomesOwnerAndIsRecordedAsCreator() throws Exception {
        var me = request("GET", "/auth/me", null);
        assertEquals(200, me.statusCode(), me.body());
        long accountId = json.readTree(me.body()).get("id").asLong();

        var created = create("Owned project", "OWNED", null);
        long id = id(created);
        var project = json.readTree(created.body());
        assertEquals(accountId, project.get("createdByAccountId").asLong());
        assertEquals("OWNER", project.get("role").stringValue());

        var read = json.readTree(request("GET", "/projects/" + id, null).body());
        assertEquals(accountId, read.get("createdByAccountId").asLong());
        assertEquals("OWNER", read.get("role").stringValue());
        JsonNode listed = null;
        for (JsonNode item : json.readTree(request("GET", "/projects?active=all", null).body())) {
            if (item.get("id").asLong() == id) listed = item;
        }
        assertNotNull(listed);
        assertEquals("OWNER", listed.get("role").stringValue());

        var memberships = new org.springframework.jdbc.core.JdbcTemplate(dataSource).queryForList(
            "SELECT account_id, role FROM project_memberships WHERE project_id = ?", id);
        assertEquals(List.of(Map.of("account_id", accountId, "role", "OWNER")), memberships);

        // Clients cannot change the creator or their own role through a project update.
        var updated = request("PUT", "/projects/" + id,
            "{\"name\":\"Renamed\",\"createdByAccountId\":999,\"role\":\"VIEWER\"}");
        assertEquals(200, updated.statusCode(), updated.body());
        assertEquals(accountId, json.readTree(updated.body()).get("createdByAccountId").asLong());
        assertEquals("OWNER", json.readTree(updated.body()).get("role").stringValue());
        assertEquals(accountId, projects.findById(id).orElseThrow().getCreatedByAccountId());
    }

    @Test
    void failedOwnerMembershipRollsBackProjectCreation() throws Exception {
        var jdbc = new org.springframework.jdbc.core.JdbcTemplate(dataSource);
        // Fails only this test's project, so concurrent fixtures are unaffected.
        jdbc.execute("""
            CREATE FUNCTION reject_rollback_membership() RETURNS trigger LANGUAGE plpgsql AS $$
            BEGIN
                IF EXISTS (SELECT 1 FROM projects WHERE id = NEW.project_id AND project_key = 'ROLLBACK') THEN
                    RAISE EXCEPTION 'Simulated membership failure';
                END IF;
                RETURN NEW;
            END $$""");
        jdbc.execute("""
            CREATE TRIGGER reject_rollback_membership BEFORE INSERT ON project_memberships
            FOR EACH ROW EXECUTE FUNCTION reject_rollback_membership()""");
        try {
            assertProblem(create("Rollback", "ROLLBACK", null), 500);
            assertEquals(0, jdbc.queryForObject(
                "SELECT count(*) FROM projects WHERE project_key = 'ROLLBACK'", Integer.class),
                "A project must never exist without its owner");
        } finally {
            jdbc.execute("DROP TRIGGER reject_rollback_membership ON project_memberships");
            jdbc.execute("DROP FUNCTION reject_rollback_membership()");
        }
    }

    @Test
    void ignoresCreatorAndRoleSuppliedOnCreate() throws Exception {
        long accountId = json.readTree(request("GET", "/auth/me", null).body()).get("id").asLong();
        var created = request("POST", "/projects",
            "{\"name\":\"Spoofed\",\"key\":\"SPOOF\",\"createdByAccountId\":999,\"role\":\"VIEWER\"}");
        trackCreated(created);
        long id = id(created);
        assertEquals(accountId, json.readTree(created.body()).get("createdByAccountId").asLong());
        assertEquals("OWNER", json.readTree(created.body()).get("role").stringValue());
        assertEquals(1, new org.springframework.jdbc.core.JdbcTemplate(dataSource).queryForObject(
            "SELECT count(*) FROM project_memberships WHERE project_id = ?", Integer.class, id));
    }

    @Test
    void archivesWithoutDeletingAndFiltersLists() throws Exception {
        assertIds("");
        assertIds("?active=false");
        assertIds("?active=all");
        long activeId = id(create("Active", "ACT", null));
        long archivedId = id(create("Archive", "ARCH", "Preserve details"));
        var original = withHistoricalTimestamps(archivedId);
        long count = projects.count();
        var response = request("DELETE", "/projects/" + archivedId, null);
        assertEquals(204, response.statusCode());
        assertEquals("", response.body());
        var archived = projects.findById(archivedId).orElseThrow();
        assertFalse(archived.isActive());
        assertEquals(original.getName(), archived.getName());
        assertEquals(original.getKey(), archived.getKey());
        assertEquals(original.getDescription(), archived.getDescription());
        assertEquals(original.getCreatedAt(), archived.getCreatedAt());
        assertTrue(archived.getUpdatedAt().isAfter(original.getUpdatedAt()));
        assertEquals(count, projects.count());
        assertIds("", activeId);
        assertIds("?active=true", activeId);
        assertIds("?active=false", archivedId);
        assertIds("?active=all", activeId, archivedId);
        var read = request("GET", "/projects/" + archivedId, null);
        assertEquals(200, read.statusCode());
        assertFalse(json.readTree(read.body()).get("active").asBoolean());
        assertProblem(request("PUT", "/projects/" + archivedId,
            "{\"name\":\"Cannot update\"}"), 404);
        assertEquals(204, request("DELETE", "/projects/" + archivedId, null).statusCode());
        var repeated = projects.findById(archivedId).orElseThrow();
        assertEquals(archived.getUpdatedAt(), repeated.getUpdatedAt());
        assertEquals(original.getName(), repeated.getName());
        assertProblem(create("Duplicate archived key", "ARCH", null), 409);
        assertProblem(request("GET", "/projects?active=invalid", null), 400);
    }

    @Test
    void acceptsBoundaryValuesAndDuplicateNames() throws Exception {
        var first = create("a".repeat(200), "A1", "b".repeat(2000));
        assertEquals(201, first.statusCode(), first.body());
        long firstId = id(first);
        var second = create("a".repeat(200), "ABCDEFGHIJ", null);
        assertEquals(201, second.statusCode(), second.body());
        assertTrue(json.readTree(second.body()).get("description").isNull());
        assertEquals(200, request("PUT", "/projects/" + firstId,
            json.writeValueAsString(Map.of("name", "c".repeat(200), "description", "d".repeat(2000)))).statusCode());
    }

    @ParameterizedTest(name = "create rejects {0}")
    @MethodSource("invalidCreateBodies")
    void rejectsInvalidCreateRequestsWithoutPersisting(String reason, String body) throws Exception {
        long count = projects.count();
        // Track even an unexpectedly accepted input so later cases remain independent.
        var response = request("POST", "/projects", body);
        trackCreated(response);
        assertProblem(response, 400);
        assertEquals(count, projects.count());
    }

    static java.util.stream.Stream<Arguments> invalidCreateBodies() {
        var json = new ObjectMapper();
        return java.util.stream.Stream.of(
            Arguments.of("missing fields", "{}"),
            Arguments.of("null body", "null"),
            Arguments.of("malformed JSON", "{invalid json}"),
            Arguments.of("null name", "{\"name\":null,\"key\":\"OK\"}"),
            Arguments.of("blank name", "{\"name\":\" \",\"key\":\"OK\"}"),
            Arguments.of("name length", json.writeValueAsString(Map.of("name", "a".repeat(201), "key", "OK"))),
            Arguments.of("missing key", "{\"name\":\"Valid\"}"),
            Arguments.of("null key", "{\"name\":\"Valid\",\"key\":null}"),
            Arguments.of("description length", json.writeValueAsString(
                Map.of("name", "Valid", "key", "OK", "description", "a".repeat(2001)))));
    }

    @ParameterizedTest(name = "rejects invalid key [{0}]")
    @ValueSource(strings = {"", " ", "A", "ABCDEFGHIJK", "lower", "Ab", "1A", "A-B", "ÄB"})
    void rejectsInvalidKeysWithoutPersisting(String key) throws Exception {
        long count = projects.count();
        assertProblem(create("Valid", key, null), 400);
        assertEquals(count, projects.count());
    }

    @ParameterizedTest(name = "update rejects {0}")
    @MethodSource("invalidUpdateBodies")
    void rejectsInvalidUpdateRequestsWithoutChangingProject(String reason, String body) throws Exception {
        long id = id(create("Keep original", "KEEP", "Keep details"));
        var original = projects.findById(id).orElseThrow();
        assertProblem(request("PUT", "/projects/" + id, body), 400);
        var after = projects.findById(id).orElseThrow();
        assertEquals(original.getName(), after.getName());
        assertEquals(original.getDescription(), after.getDescription());
        assertEquals(original.getUpdatedAt(), after.getUpdatedAt());
    }

    static java.util.stream.Stream<Arguments> invalidUpdateBodies() {
        var json = new ObjectMapper();
        return java.util.stream.Stream.of(
            Arguments.of("missing name", "{}"),
            Arguments.of("null body", "null"),
            Arguments.of("malformed JSON", "{invalid json}"),
            Arguments.of("null name", "{\"name\":null}"),
            Arguments.of("blank name", "{\"name\":\" \"}"),
            Arguments.of("name length", json.writeValueAsString(Map.of("name", "a".repeat(201)))),
            Arguments.of("description length", json.writeValueAsString(
                Map.of("name", "Valid", "description", "a".repeat(2001)))));
    }

    @Test
    void rejectsUnknownAndMalformedIds() throws Exception {
        for (String method : List.of("GET", "PUT", "DELETE")) {
            String body = method.equals("PUT") ? "{\"name\":\"Valid\"}" : null;
            assertProblem(request(method, "/projects/" + Long.MAX_VALUE, body), 404);
            assertProblem(request(method, "/projects/not-an-id", body), 400);
        }
    }

    @Test
    void duplicateCreationPreservesOriginalProject() throws Exception {
        long id = id(create("Original", "UNIQUE", null));
        assertProblem(create("Duplicate", "UNIQUE", null), 409);
        assertEquals("Original", projects.findById(id).orElseThrow().getName());
    }

    @Test
    void concurrentDuplicateWaitsForWinningTransactionThenReturnsConflict() throws Exception {
        var pool = Executors.newSingleThreadExecutor();
        try (var winner = dataSource.getConnection()) {
            winner.setAutoCommit(false);
            try {
                try (var statement = winner.createStatement(); var row = statement.executeQuery("""
                        WITH creator AS (
                            INSERT INTO accounts(email, display_name, password_hash, created_at, updated_at)
                            VALUES (gen_random_uuid() || '@example.com', 'Race fixture', 'test-fixture-hash',
                                    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                            RETURNING id)
                        INSERT INTO projects(name, project_key, created_at, updated_at, created_by_account_id)
                        SELECT 'Winning project', 'RACE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, id FROM creator
                        RETURNING id
                        """)) {
                    assertTrue(row.next());
                    createdIds.add(row.getLong(1));
                }
                var losingRequest = pool.submit(() -> create("Losing project", "RACE", null));
                awaitBlockedInsert(winner);
                assertFalse(losingRequest.isDone(), "The HTTP insert must wait for the uncommitted winner");
                winner.commit();
                assertProblem(losingRequest.get(15, TimeUnit.SECONDS), 409);
                var persisted = projects.findAll().stream().filter(p -> p.getKey().equals("RACE")).toList();
                assertEquals(1, persisted.size());
                assertEquals("Winning project", persisted.get(0).getName());
            } finally {
                winner.rollback();
            }
        } finally {
            pool.shutdownNow();
            assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS), "HTTP worker did not terminate");
        }
    }

    private void awaitBlockedInsert(Connection winner) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(8);
        try (var statement = winner.createStatement()) {
            statement.setQueryTimeout(2);
            while (System.nanoTime() < deadline) {
                try (var rows = statement.executeQuery("""
                        SELECT EXISTS (
                            SELECT 1 FROM pg_stat_activity
                            WHERE datname = current_database()
                              AND pg_backend_pid() = ANY(pg_blocking_pids(pid))
                        )
                        """)) {
                    assertTrue(rows.next());
                    if (rows.getBoolean(1)) return;
                }
                Thread.sleep(25);
            }
        }
        fail("HTTP insert never reached the unique-key lock held by the winning transaction");
    }

    private github.salemby.tododevops.workmanagement.domain.Project withHistoricalTimestamps(long id) {
        // A known older fixture proves advancement without depending on wall-clock granularity.
        new org.springframework.jdbc.core.JdbcTemplate(dataSource).update("""
            UPDATE projects SET created_at = TIMESTAMPTZ '2000-01-01 00:00:00Z',
                                updated_at = TIMESTAMPTZ '2000-01-01 00:00:00Z' WHERE id = ?
            """, id);
        return projects.findById(id).orElseThrow();
    }

    private HttpResponse<String> create(String name, String key, String description) throws Exception {
        var fields = new java.util.HashMap<String, Object>();
        fields.put("name", name);
        fields.put("key", key);
        fields.put("description", description);
        var response = request("POST", "/projects", json.writeValueAsString(fields));
        trackCreated(response);
        return response;
    }

    private void trackCreated(HttpResponse<String> response) {
        if (response.statusCode() == 201) createdIds.add(json.readTree(response.body()).get("id").asLong());
    }

    private long id(HttpResponse<String> response) {
        assertEquals(201, response.statusCode(), response.body());
        return json.readTree(response.body()).get("id").asLong();
    }

    private void assertIds(String query, Long... expected) throws Exception {
        var response = request("GET", "/projects" + query, null);
        assertEquals(200, response.statusCode());
        var body = json.readTree(response.body());
        assertTrue(body.isArray());
        var ids = new java.util.HashSet<Long>();
        for (JsonNode item : body) ids.add(item.get("id").asLong());
        assertEquals(expected.length, body.size());
        assertEquals(Set.of(expected), ids);
    }

    private void assertProblem(HttpResponse<String> response, int status) {
        assertEquals(status, response.statusCode(), response.body());
        assertTrue(response.headers().firstValue("Content-Type").orElseThrow().startsWith("application/problem+json"));
        var body = json.readTree(response.body());
        assertEquals(status, body.get("status").asInt());
        assertEquals("urn:projetotodo:problem:" + status, body.get("type").stringValue());
        assertTrue(body.get("errors").isArray());
        assertFalse(response.body().contains("SQLException"));
        assertFalse(response.body().contains("uk_projects_key"));
    }

    private HttpResponse<String> request(String method, String path, String body) throws Exception {
        var builder = TestHttp.request(URI.create("http://127.0.0.1:"
            + environment.getProperty("local.server.port") + path));
        if (body != null) builder.header("Content-Type", "application/json");
        return client.send(builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
            : HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }
}
