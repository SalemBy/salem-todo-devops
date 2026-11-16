package github.salemby.tododevops;

import github.salemby.tododevops.support.TestHttp;
import github.salemby.tododevops.support.IsolatedPostgresSchema;
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
    @org.junit.jupiter.api.BeforeEach
    void createOwner() {
        owner = ownerProjects.saveAndFlush(new github.salemby.tododevops.workmanagement.domain.Project("Task owner", "T" + java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase(), null));
    }
    @Autowired Environment environment;
    @Autowired TaskRepository tasks;
    @Autowired ProjectRepository projects;
    private final Set<Long> taskIds = new HashSet<>();
    private final Set<Long> projectIds = new HashSet<>();
    private final ObjectMapper json = new ObjectMapper();
    private final HttpClient client = TestHttp.client();

    @AfterEach
    void cleanup() {
        tasks.deleteAll(tasks.findAllByProjectId(owner.getId()));
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
        long id = projects.saveAndFlush(new Project("Original", "BASE", null)).getId();
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
