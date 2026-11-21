package github.salemby.tododevops;

import github.salemby.tododevops.support.TestHttp;
import github.salemby.tododevops.support.IsolatedPostgresSchema;
import github.salemby.tododevops.support.TaskRows;
import org.springframework.test.context.ContextConfiguration;

import github.salemby.tododevops.workmanagement.domain.Project;
import github.salemby.tododevops.workmanagement.domain.Task;
import github.salemby.tododevops.workmanagement.infrastructure.persistence.ProjectRepository;
import github.salemby.tododevops.workmanagement.infrastructure.persistence.TaskRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = SalemTodoDevOpsApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@org.springframework.test.context.ActiveProfiles("postgres-test")
@ContextConfiguration(initializers = IsolatedPostgresSchema.class)
class PostgresRequiredFieldsIT {
    private github.salemby.tododevops.workmanagement.domain.Project owner;
    @Autowired github.salemby.tododevops.workmanagement.infrastructure.persistence.ProjectRepository ownerProjects;
    @Autowired github.salemby.tododevops.workmanagement.infrastructure.persistence.ProjectMembershipRepository memberships;
    @org.junit.jupiter.api.BeforeEach
    void createOwner() {
        // Owned by the HTTP test account, so its requests pass the membership check.
        long accountId = github.salemby.tododevops.support.TestHttp.accountId(
            java.net.URI.create("http://127.0.0.1:" + environment.getProperty("local.server.port")));
        owner = github.salemby.tododevops.support.TestProjects.owned(ownerProjects, memberships, accountId,
            "Task owner", github.salemby.tododevops.support.TestProjects.uniqueKey("T"));
    }
    @Autowired Environment environment;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
    @Autowired TaskRepository tasks;
    @Autowired ProjectRepository projects;
    private final Set<Long> taskIds = new HashSet<>();
    private final Set<Long> projectIds = new HashSet<>();
    private final ObjectMapper json = new ObjectMapper();
    private final HttpClient client = TestHttp.client();

    @AfterEach
    void cleanup() {
        TaskRows.delete(jdbc, owner.getId());
        tasks.deleteAllById(taskIds);
        projects.deleteAllById(projectIds);
        ownerProjects.deleteById(owner.getId());
    }

    @ParameterizedTest(name = "task title: {0}")
    @MethodSource("blankValues")
    void rejectsBlankTaskTitleOnCreateAndUpdate(String reason, String title, boolean present) throws Exception {
        Task task = new Task(owner);
        task.setTitle("Original");
        long id = tasks.saveAndFlush(task).getId();
        taskIds.add(id);
        var before = tasks.findById(id).orElseThrow();
        var fields = new HashMap<String, Object>();
        if (present) fields.put("title", title);
        assertRejected("POST", "/projects/" + owner.getId() + "/tasks", fields, "title", false);
        fields.put("status", "PENDING");
        assertRejected("PUT", "/projects/" + owner.getId() + "/tasks/" + id, fields, "title", false);
        assertEquals("Original", tasks.findById(id).orElseThrow().getTitle());
        assertEquals(before.getUpdatedAt(), tasks.findById(id).orElseThrow().getUpdatedAt());
    }

    @ParameterizedTest(name = "project name: {0}")
    @MethodSource("blankValues")
    void rejectsBlankProjectNameOnCreateAndUpdate(String reason, String name, boolean present) throws Exception {
        long id = github.salemby.tododevops.support.TestProjects.owned(projects, memberships,
            owner.getCreatedByAccountId(), "Original", "BASE").getId();
        projectIds.add(id);
        var before = projects.findById(id).orElseThrow();
        var fields = new HashMap<String, Object>();
        if (present) fields.put("name", name);
        fields.put("key", "VALID");
        assertRejected("POST", "/projects", fields, "name", true);
        fields.remove("key");
        assertRejected("PUT", "/projects/" + id, fields, "name", true);
        assertEquals("Original", projects.findById(id).orElseThrow().getName());
        assertEquals(before.getUpdatedAt(), projects.findById(id).orElseThrow().getUpdatedAt());
    }

    @ParameterizedTest(name = "project key: {0}")
    @MethodSource("blankValues")
    void rejectsBlankProjectKeyIndependentlyOfName(String reason, String key, boolean present) throws Exception {
        var fields = new HashMap<String, Object>();
        fields.put("name", "Valid project name");
        if (present) fields.put("key", key);
        assertRejected("POST", "/projects", fields, "key", true);
    }

    @ParameterizedTest(name = "NUL in {0}: {1}")
    @MethodSource("nulFields")
    void rejectsNulOnCreateAndUpdateWithoutMutation(boolean project, String field) throws Exception {
        Task task = new Task(owner);
        task.setTitle("Original task");
        long taskId = tasks.saveAndFlush(task).getId();
        String collection = project ? "/projects" : "/projects/" + owner.getId() + "/tasks";
        String resource = project ? "/projects/" + owner.getId() : collection + "/" + taskId;
        var before = read(resource);
        var fields = new HashMap<String, Object>();
        fields.put(project ? "name" : "title", "Visible text");
        fields.put("description", "Original description");
        if (project) fields.put("key", "NULTEST");
        fields.put(field, "Visible" + (char) 0 + "text");
        assertRejected("POST", collection, fields, field, project);
        fields.remove("key");
        if (!project) fields.put("status", "COMPLETED");
        assertRejected("PUT", resource, fields, field, project);
        assertEquals(before, read(resource), "Rejected update must preserve every field and timestamp");
    }

    static java.util.stream.Stream<Arguments> nulFields() {
        return java.util.stream.Stream.of(Arguments.of(false, "title"), Arguments.of(false, "description"),
            Arguments.of(true, "name"), Arguments.of(true, "description"));
    }

    @org.junit.jupiter.api.Test
    void preservesUnicodeAndOptionalNullDescriptions() throws Exception {
        String text = "Caf\u00e9 \u65e5\u672c\u8a9e \ud83d\ude80\nSecond line";
        // Exercise JSON binding and persistence, not just a validator instance.
        for (boolean isProject : java.util.List.of(true, false)) {
            String collection = isProject ? "/projects" : "/projects/" + owner.getId() + "/tasks";
            var fields = new HashMap<String, Object>();
            fields.put(isProject ? "name" : "title", text);
            fields.put("description", text);
            if (isProject) fields.put("key", "UNICODE");
            var created = client.send(TestHttp.request(URI.create("http://127.0.0.1:" + environment.getProperty("local.server.port") + collection))
                .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(fields))).build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(201, created.statusCode(), created.body());
            long id = json.readTree(created.body()).get("id").asLong();
            (isProject ? projectIds : taskIds).add(id);
            String resource = collection + "/" + id;
            assertEquals(text, read(resource).get("description").stringValue());
            fields.put("description", null);
            if (!isProject) fields.put("status", "PENDING");
            var updated = client.send(TestHttp.request(URI.create("http://127.0.0.1:" + environment.getProperty("local.server.port") + resource))
                .header("Content-Type", "application/json").PUT(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(fields))).build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(200, updated.statusCode(), updated.body());
            assertEquals(text, read(resource).get(isProject ? "name" : "title").stringValue());
            assertTrue(read(resource).get("description").isNull());
        }
    }

    private tools.jackson.databind.JsonNode read(String path) throws Exception {
        var response = client.send(TestHttp.request(URI.create("http://127.0.0.1:" + environment.getProperty("local.server.port") + path)).GET().build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode(), response.body());
        return json.readTree(response.body());
    }

    static java.util.stream.Stream<Arguments> blankValues() {
        return java.util.stream.Stream.of(
            Arguments.of("omitted", null, false),
            Arguments.of("null", null, true),
            Arguments.of("empty", "", true),
            Arguments.of("space", " ", true),
            Arguments.of("tabs and newlines", "\t\r\n", true),
            Arguments.of("non-breaking space", "\u00a0", true),
            Arguments.of("figure space", "\u2007", true),
            Arguments.of("narrow non-breaking space", "\u202f", true),
            Arguments.of("zero-width space", "\u200b", true));
    }

    private void assertRejected(String method, String path, Map<String, ?> fields,
            String field, boolean project) throws Exception {
        long count = project ? projects.count() : tasks.count();
        var request = TestHttp.request(URI.create("http://127.0.0.1:"
            + environment.getProperty("local.server.port") + path))
            .header("Content-Type", "application/json")
            .method(method, HttpRequest.BodyPublishers.ofString(json.writeValueAsString(fields))).build();
        var response = client.send(request, HttpResponse.BodyHandlers.ofString());
        // Clean up unexpected writes even when the regression assertion fails.
        if (response.statusCode() == 201) {
            long id = json.readTree(response.body()).get("id").asLong();
            (project ? projectIds : taskIds).add(id);
        }
        assertEquals(400, response.statusCode(), "Expected rejection of " + json.writeValueAsString(fields)
            + " but received " + response.body());
        var problem = json.readTree(response.body());
        assertTrue(problem.get("errors").valueStream().anyMatch(e -> field.equals(e.path("field").stringValue())), response.body());
        assertEquals(count, project ? projects.count() : tasks.count());
    }
}
