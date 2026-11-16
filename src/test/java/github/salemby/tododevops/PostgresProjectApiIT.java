package github.salemby.tododevops;

import github.salemby.tododevops.workmanagement.infrastructure.persistence.ProjectRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = SalemTodoDevOpsApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@org.springframework.test.context.ActiveProfiles("postgres-test")
class PostgresProjectApiIT {
    @Autowired Environment environment;
    @Autowired ProjectRepository projects;
    private final ObjectMapper json = new ObjectMapper();
    private final HttpClient client = HttpClient.newHttpClient();
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
        assertEquals(7, project.size());
        assertEquals("Todo platform", project.get("name").asText());
        assertEquals("TODO", project.get("key").asText());
        assertEquals("Original details", project.get("description").asText());
        assertTrue(project.get("active").asBoolean());
        assertNotNull(Instant.parse(project.get("createdAt").asText()));
        var original = projects.findById(id).orElseThrow();
        var read = request("GET", "/projects/" + id, null);
        assertEquals(200, read.statusCode());
        assertEquals("TODO", json.readTree(read.body()).get("key").asText());

        var updatedResponse = request("PUT", "/projects/" + id,
            "{\"name\":\"Updated\",\"description\":\"New details\",\"key\":\"HACK\","
                + "\"active\":false,\"id\":999,\"createdAt\":\"2000-01-01T00:00:00Z\"}");
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
        assertEquals("TODO", updatedJson.get("key").asText());
        assertTrue(java.time.Duration.between(updated.getUpdatedAt(),
            Instant.parse(updatedJson.get("updatedAt").asText())).abs().toNanos() < 1000);

        assertEquals(200, request("PUT", "/projects/" + id, "{\"name\":\"No description\"}").statusCode());
        assertNull(projects.findById(id).orElseThrow().getDescription());
        assertEquals(200, request("PUT", "/projects/" + id,
            "{\"name\":\"Null description\",\"description\":null}").statusCode());
        assertNull(projects.findById(id).orElseThrow().getDescription());
    }

    @Test
    void archivesWithoutDeletingAndFiltersLists() throws Exception {
        assertIds("");
        assertIds("?active=false");
        assertIds("?active=all");
        long activeId = id(create("Active", "ACT", null));
        long archivedId = id(create("Archive", "ARCH", "Preserve details"));
        var original = projects.findById(archivedId).orElseThrow();
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

    @Test
    void rejectsInvalidCreateRequestsWithoutPersisting() throws Exception {
        long count = projects.count();
        var bodies = List.of("{}", "null", "{invalid json}",
            "{\"name\":null,\"key\":\"OK\"}", "{\"name\":\" \",\"key\":\"OK\"}",
            json.writeValueAsString(Map.of("name", "a".repeat(201), "key", "OK")),
            "{\"name\":\"Valid\"}", "{\"name\":\"Valid\",\"key\":null}",
            json.writeValueAsString(Map.of("name", "Valid", "key", "OK", "description", "a".repeat(2001))));
        for (String body : bodies) {
            assertProblem(request("POST", "/projects", body), 400);
        }
        for (String key : List.of("", " ", "A", "ABCDEFGHIJK", "lower", "Ab", "1A", "A-B", "ÄB")) {
            assertProblem(create("Valid", key, null), 400);
        }
        assertEquals(count, projects.count());
    }

    @Test
    void rejectsInvalidUpdateRequestsWithoutChangingProject() throws Exception {
        long id = id(create("Keep original", "KEEP", "Keep details"));
        var original = projects.findById(id).orElseThrow();
        for (String body : List.of("{}", "null", "{invalid json}", "{\"name\":null}", "{\"name\":\" \"}",
                json.writeValueAsString(Map.of("name", "a".repeat(201))),
                json.writeValueAsString(Map.of("name", "Valid", "description", "a".repeat(2001))))) {
            assertProblem(request("PUT", "/projects/" + id, body), 400);
        }
        var after = projects.findById(id).orElseThrow();
        assertEquals(original.getName(), after.getName());
        assertEquals(original.getDescription(), after.getDescription());
        assertEquals(original.getUpdatedAt(), after.getUpdatedAt());
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
    void duplicateCreationReturnsConflictIncludingConcurrentRequests() throws Exception {
        long id = id(create("Original", "UNIQUE", null));
        assertProblem(create("Duplicate", "UNIQUE", null), 409);
        assertEquals("Original", projects.findById(id).orElseThrow().getName());
        var pool = Executors.newFixedThreadPool(2);
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        try {
            var jobs = java.util.stream.IntStream.range(0, 2).mapToObj(i -> pool.submit(() -> {
                ready.countDown();
                if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Start timed out");
                return create("Concurrent " + i, "RACE", null);
            })).toList();
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();
            var responses = List.of(jobs.get(0).get(15, TimeUnit.SECONDS), jobs.get(1).get(15, TimeUnit.SECONDS));
            assertEquals(List.of(201, 409), responses.stream().map(HttpResponse::statusCode).sorted().toList());
            assertProblem(responses.stream().filter(r -> r.statusCode() == 409).findFirst().orElseThrow(), 409);
            assertEquals(1, projects.findAll().stream().filter(p -> p.getKey().equals("RACE")).count());
        } finally {
            start.countDown();
            pool.shutdownNow();
            assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));
        }
    }

    private HttpResponse<String> create(String name, String key, String description) throws Exception {
        var fields = new java.util.HashMap<String, Object>();
        fields.put("name", name);
        fields.put("key", key);
        fields.put("description", description);
        var response = request("POST", "/projects", json.writeValueAsString(fields));
        if (response.statusCode() == 201) createdIds.add(json.readTree(response.body()).get("id").asLong());
        return response;
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
        assertEquals("urn:projetotodo:problem:" + status, body.get("type").asText());
        assertTrue(body.get("errors").isArray());
        assertFalse(response.body().contains("SQLException"));
        assertFalse(response.body().contains("uk_projects_key"));
    }

    private HttpResponse<String> request(String method, String path, String body) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:"
            + environment.getProperty("local.server.port") + path)).timeout(java.time.Duration.ofSeconds(15));
        if (body != null) builder.header("Content-Type", "application/json");
        return client.send(builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
            : HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }
}
