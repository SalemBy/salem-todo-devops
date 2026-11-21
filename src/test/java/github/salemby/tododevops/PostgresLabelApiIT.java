package github.salemby.tododevops;

import github.salemby.tododevops.support.IsolatedPostgresSchema;
import github.salemby.tododevops.support.TaskRows;
import github.salemby.tododevops.support.TestHttp;
import github.salemby.tododevops.workmanagement.infrastructure.persistence.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import tools.jackson.databind.*;
import java.net.URI;
import java.net.http.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = SalemTodoDevOpsApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("postgres-test")
@ContextConfiguration(initializers = IsolatedPostgresSchema.class)
class PostgresLabelApiIT {
    @Autowired Environment environment;
    @Autowired ProjectRepository projects;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
    @Autowired LabelRepository labels;
    final ObjectMapper json = new ObjectMapper();
    final List<Long> owners = new ArrayList<>();

    @AfterEach void cleanup() {
        for (long id : owners) {
            TaskRows.delete(jdbc, id);
            labels.deleteAll(labels.findAllByProjectIdOrderById(id));
            projects.deleteById(id);
        }
    }

    @Test void labelLifecyclePreservesAssociationsAndAllowsExplicitRemoval() throws Exception {
        long p = project();
        String base = "/projects/" + p;
        var label = call("POST", base + "/labels", "{\"name\":\" Backend \"}", 201);
        long id = label.get("id").asLong();
        String labelPath = base + "/labels/" + id;
        assertEquals("Backend", label.get("name").stringValue());
        assertEquals(p, label.get("projectId").asLong());
        assertEquals(1, call("GET", base + "/labels", null, 200).size());
        var task = call("POST", base + "/tasks", "{\"title\":\"Labeled\",\"labelIds\":[" + id + "]}", 201);
        String taskPath = base + "/tasks/" + task.get("id").asLong();
        assertEquals(id, task.get("labels").get(0).get("id").asLong());
        var page = call("GET", base + "/tasks?size=1", null, 200);
        assertEquals(1, page.get("totalElements").asLong());
        assertEquals(call("GET", taskPath, null, 200), page.get("items").get(0));
        call("PUT", taskPath + "/labels/" + id, null, 204);
        assertEquals(1, call("GET", taskPath + "/labels", null, 200).size());
        call("PUT", labelPath, "{\"name\":\"API\"}", 200);
        assertEquals("API", call("GET", taskPath, null, 200).get("labels").get(0).get("name").stringValue());
        call("DELETE", labelPath, null, 204);
        var archived = call("GET", labelPath, null, 200);
        call("DELETE", labelPath, null, 204);
        assertEquals(archived, call("GET", labelPath, null, 200));
        assertEquals(0, call("GET", base + "/labels", null, 200).size());
        assertEquals(1, call("GET", base + "/labels?active=false", null, 200).size());
        assertFalse(call("GET", taskPath + "/labels", null, 200).get(0).get("active").asBoolean());
        call("PUT", taskPath, "{\"title\":\"Keep historical label\",\"status\":\"PENDING\",\"labelIds\":[" + id + "]}", 200);
        call("PUT", taskPath, "{\"title\":\"Omitted labels\",\"status\":\"PENDING\"}", 200);
        assertEquals(1, call("GET", taskPath + "/labels", null, 200).size());
        call("POST", base + "/labels", "{\"name\":\"api\"}", 409);
        call("PUT", labelPath, "{\"name\":\"Rename archived\"}", 404);
        call("POST", base + "/tasks", "{\"title\":\"New association\",\"labelIds\":[" + id + "]}", 409);
        call("DELETE", taskPath + "/labels/" + id, null, 204);
        call("DELETE", taskPath + "/labels/" + id, null, 204);
        call("PUT", taskPath + "/labels/" + id, null, 409);
        assertEquals(0, call("GET", taskPath + "/labels", null, 200).size());
        assertEquals(1, TaskRows.count(jdbc, p));
    }

    @Test void rejectsCrossProjectAndInvalidReplacementsAtomically() throws Exception {
        long p = project(), other = project();
        String base = "/projects/" + p;
        long a = label(p, "Backend"), b = label(p, "Frontend"), foreign = label(other, "Backend");
        var task = call("POST", base + "/tasks", "{\"title\":\"Original\",\"type\":\"CHORE\",\"labelIds\":[" + a + "]}", 201);
        String path = base + "/tasks/" + task.get("id").asLong();
        var before = call("GET", path, null, 200);
        for (String ids : List.of("null", "[0]", "[-1]", "[1.5]", "[\"1\"]", "[null]", "[" + a + "," + a + "]", "{}", "[9223372036854775808]")) {
            call("PUT", path, "{\"title\":\"Changed\",\"status\":\"COMPLETED\",\"labelIds\":" + ids + "}", 400);
            assertEquals(before, call("GET", path, null, 200));
        }
        call("PUT", path, "{\"title\":\"Changed\",\"status\":\"COMPLETED\",\"priority\":\"CRITICAL\",\"labelIds\":[" + b + "," + foreign + "]}", 404);
        assertEquals(before, call("GET", path, null, 200));
        call("POST", base + "/tasks", "{\"title\":\"Invalid owner\",\"labelIds\":[" + foreign + "]}", 404);
        assertEquals(1, TaskRows.count(jdbc, p));
        call("PUT", path + "/labels/" + foreign, null, 404);
        call("DELETE", path + "/labels/" + foreign, null, 404);
        call("GET", base + "/labels/" + foreign, null, 404);
        call("PUT", base + "/labels/" + foreign, "{\"name\":\"Wrong\"}", 404);
        call("DELETE", base + "/labels/" + foreign, null, 404);
        call("PUT", base + "/labels/" + b, "{\"name\":\"BACKEND\"}", 409);
        assertEquals("Frontend", call("GET", base + "/labels/" + b, null, 200).get("name").stringValue());
        call("PUT", path, "{\"title\":\"Replace\",\"status\":\"PENDING\",\"labelIds\":[" + b + "]}", 200);
        assertEquals(b, call("GET", path + "/labels", null, 200).get(0).get("id").asLong());
        call("PUT", path, "{\"title\":\"Clear\",\"status\":\"PENDING\",\"labelIds\":[]}", 200);
        assertEquals(0, call("GET", path + "/labels", null, 200).size());
    }

    @Test void validatesNamesAndProtectsArchivedProjectsAndInactiveTasks() throws Exception {
        long p = project();
        String base = "/projects/" + p;
        long id = label(p, "é".repeat(50));
        for (String name : Arrays.asList("", " ", "\u200b", "a".repeat(51), "Visible" + (char) 0 + "text", null)) {
            var fields = new HashMap<String, Object>();
            fields.put("name", name);
            var problem = call("POST", base + "/labels", json.writeValueAsString(fields), 400);
            assertTrue(problem.get("errors").valueStream().anyMatch(e -> "name".equals(e.get("field").stringValue())));
            var before = call("GET", base + "/labels/" + id, null, 200);
            call("PUT", base + "/labels/" + id, json.writeValueAsString(fields), 400);
            assertEquals(before, call("GET", base + "/labels/" + id, null, 200));
        }
        call("GET", base + "/labels?active=bad", null, 400);
        var task = call("POST", base + "/tasks", "{\"title\":\"History\",\"labelIds\":[" + id + "]}", 201);
        String taskPath = base + "/tasks/" + task.get("id").asLong();
        call("DELETE", taskPath, null, 204);
        call("PUT", taskPath + "/labels/" + id, null, 404);
        call("DELETE", taskPath + "/labels/" + id, null, 404);
        var before = call("GET", taskPath, null, 200);
        call("DELETE", base, null, 204);
        call("POST", base + "/labels", "{\"name\":\"Blocked\"}", 409);
        call("PUT", base + "/labels/" + id, "{\"name\":\"Blocked\"}", 409);
        call("DELETE", base + "/labels/" + id, null, 409);
        call("PUT", taskPath + "/labels/" + id, null, 409);
        call("DELETE", taskPath + "/labels/" + id, null, 409);
        assertEquals(before, call("GET", taskPath, null, 200));
        assertEquals(1, call("GET", taskPath + "/labels", null, 200).size());
        assertEquals(1, call("GET", base + "/labels?active=all", null, 200).size());
    }

    private long project() throws Exception {
        long id = call("POST", "/projects", "{\"name\":\"Labels\",\"key\":\"L"
            + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase() + "\"}", 201).get("id").asLong();
        owners.add(id);
        return id;
    }
    private long label(long project, String name) throws Exception {
        return call("POST", "/projects/" + project + "/labels", json.writeValueAsString(Map.of("name", name)), 201).get("id").asLong();
    }
    private JsonNode call(String method, String path, String body, int status) throws Exception {
        var request = TestHttp.request(URI.create("http://127.0.0.1:" + environment.getProperty("local.server.port") + path))
            .header("Content-Type", "application/json")
            .method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body)).build();
        var response = TestHttp.client().send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(status, response.statusCode(), method + " " + path + ": " + response.body());
        if (status >= 400) {
            assertTrue(response.headers().firstValue("Content-Type").orElseThrow().startsWith("application/problem+json"));
            var problem = json.readTree(response.body());
            assertEquals(status, problem.get("status").asInt());
            assertEquals("urn:projetotodo:problem:" + status, problem.get("type").stringValue());
        }
        if (status == 201) assertTrue(response.headers().firstValue("Location").isPresent());
        return status == 204 ? null : json.readTree(response.body());
    }
}
