package github.salemby.projetotodo;

import github.salemby.projetotodo.entity.Project;
import github.salemby.projetotodo.entity.Task;
import github.salemby.projetotodo.repository.ProjectRepository;
import github.salemby.projetotodo.repository.TaskRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = ProjetoTodoApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
abstract class RequiredFieldsHttpContract {
    @Autowired Environment environment;
    @Autowired TaskRepository tasks;
    @Autowired ProjectRepository projects;
    private final Set<Long> taskIds = new HashSet<>();
    private final Set<Long> projectIds = new HashSet<>();
    private final ObjectMapper json = new ObjectMapper();
    private final HttpClient client = HttpClient.newHttpClient();

    @AfterEach
    void cleanup() {
        tasks.deleteAllById(taskIds);
        projects.deleteAllById(projectIds);
    }

    @Test
    void rejectsEveryBlankTaskTitleOnCreateAndUpdate() throws Exception {
        Task task = new Task();
        task.setTitle("Original");
        long id = tasks.saveAndFlush(task).getId();
        taskIds.add(id);
        var before = tasks.findById(id).orElseThrow();
        for (String title : blankValues()) {
            var fields = new HashMap<String, Object>();
            fields.put("title", title);
            assertRejected("POST", "/tasks", fields, "title", false);
            fields.put("status", "PENDING");
            assertRejected("PUT", "/tasks/" + id, fields, "title", false);
        }
        assertRejected("POST", "/tasks", Map.of(), "title", false);
        assertRejected("PUT", "/tasks/" + id, Map.of("status", "PENDING"), "title", false);
        assertEquals("Original", tasks.findById(id).orElseThrow().getTitle());
        assertEquals(before.getUpdatedAt(), tasks.findById(id).orElseThrow().getUpdatedAt());
    }

    @Test
    void rejectsEveryBlankProjectNameOnCreateAndUpdate() throws Exception {
        long id = projects.saveAndFlush(new Project("Original", "BASE", null)).getId();
        projectIds.add(id);
        var before = projects.findById(id).orElseThrow();
        for (String name : blankValues()) {
            var fields = new HashMap<String, Object>();
            fields.put("name", name);
            fields.put("key", "VALID");
            assertRejected("POST", "/projects", fields, "name", true);
            fields.remove("key");
            assertRejected("PUT", "/projects/" + id, fields, "name", true);
        }
        assertRejected("POST", "/projects", Map.of("key", "VALID"), "name", true);
        assertRejected("PUT", "/projects/" + id, Map.of(), "name", true);
        assertEquals("Original", projects.findById(id).orElseThrow().getName());
        assertEquals(before.getUpdatedAt(), projects.findById(id).orElseThrow().getUpdatedAt());
    }

    @Test
    void rejectsEveryBlankProjectKeyIndependentlyOfName() throws Exception {
        for (String key : blankValues()) {
            var fields = new HashMap<String, Object>();
            fields.put("name", "Valid project name");
            fields.put("key", key);
            assertRejected("POST", "/projects", fields, "key", true);
        }
        assertRejected("POST", "/projects", Map.of("name", "Valid project name"), "key", true);
    }

    private List<String> blankValues() {
        return Arrays.asList(null, "", " ", "   ", "\t\r\n", "\u00a0", "\u2007", "\u202f", "\u200b");
    }

    private void assertRejected(String method, String path, Map<String, ?> fields,
            String field, boolean project) throws Exception {
        long count = project ? projects.count() : tasks.count();
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:"
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
        assertTrue(problem.get("errors").valueStream().anyMatch(e -> field.equals(e.path("field").asText())), response.body());
        assertEquals(count, project ? projects.count() : tasks.count());
    }
}
