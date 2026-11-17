package github.salemby.tododevops;

import github.salemby.tododevops.support.TestHttp;
import github.salemby.tododevops.support.IsolatedPostgresSchema;
import org.springframework.test.context.ContextConfiguration;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import github.salemby.tododevops.workmanagement.infrastructure.persistence.*;
import java.net.URI;
import java.net.http.*;
import java.util.*;
import tools.jackson.databind.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = SalemTodoDevOpsApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@org.springframework.test.context.ActiveProfiles("postgres-test")
@ContextConfiguration(initializers = IsolatedPostgresSchema.class)
class PostgresTaskFilterIT {
    @Autowired Environment environment;
    @Autowired TaskRepository tasks;
    @Autowired ProjectRepository projects;
    @Autowired LabelRepository labels;
    final ObjectMapper json = new ObjectMapper();
    final List<Long> owners = new ArrayList<>();
    JsonNode call(String method, String path, String body, int status) throws Exception {
        var request = TestHttp.request(URI.create("http://127.0.0.1:" + environment.getProperty("local.server.port") + path))
            .header("Content-Type", "application/json").method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body)).build();
        var response = TestHttp.client().send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(status, response.statusCode(), method + " " + path + ": " + response.body());
        return status == 204 ? null : json.readTree(response.body());
    }
    long project() throws Exception {
        long id = call("POST", "/projects", "{\"name\":\"Owner\",\"key\":\"P" + UUID.randomUUID().toString().replace("-", "").substring(0,8).toUpperCase() + "\"}", 201).get("id").asLong();
        owners.add(id); return id;
    }
    JsonNode task(long project) throws Exception {
        return call("POST", "/projects/"+project+"/tasks", "{\"projectId\":" + project + ",\"title\":\"Task\"}", 201);
    }
    @AfterEach void cleanup() {
        for (Long id : owners) { tasks.deleteAll(tasks.findAllByProjectId(id)); labels.deleteAll(labels.findAllByProjectIdOrderById(id)); projects.deleteById(id); }
    }

    @Test void combinesFiltersBeforeCountingAndPagingWithoutDuplicatingLabels() throws Exception {
        long p = project(), other = project();
        String base = "/projects/" + p;
        long label = call("POST", base + "/labels", "{\"name\":\"Backend\"}", 201).get("id").asLong();
        long extra = call("POST", base + "/labels", "{\"name\":\"API\"}", 201).get("id").asLong();
        String body = "{\"title\":\"Match\",\"type\":\"BUG\",\"priority\":\"HIGH\",\"dueDate\":\"2026-06-15\",\"labelIds\":[" + label + "," + extra + "]}";
        var a = call("POST", base + "/tasks", body, 201);
        var b = call("POST", base + "/tasks", body, 201);
        task(p); task(other);
        String query = "?status=PENDING&type=BUG&priority=HIGH&labelId=" + label + "&dueFrom=2026-06-15&dueTo=2026-06-15&size=1";
        var page = call("GET", base + "/tasks" + query, null, 200);
        assertEquals(2, page.get("totalElements").asInt());
        assertEquals(2, page.get("totalPages").asInt());
        assertEquals(a.get("id"), page.get("items").get(0).get("id"));
        assertEquals(2, page.get("items").get(0).get("labels").size());
        assertEquals(b.get("id"), call("GET", base + "/tasks" + query + "&page=1", null, 200).get("items").get(0).get("id"));
        for (String filter : List.of("status=COMPLETED", "type=FEATURE", "priority=CRITICAL", "dueFrom=2026-06-16", "dueTo=2026-06-14", "labelId=9223372036854775807"))
            assertEquals(0, call("GET", base + "/tasks?" + filter, null, 200).get("totalElements").asInt());
        assertEquals(0, call("GET", "/projects/" + other + "/tasks?labelId=" + label, null, 200).get("totalElements").asInt());
        call("DELETE", base + "/labels/" + label, null, 204);
        call("DELETE", base + "/tasks/" + a.get("id").asLong(), null, 204);
        assertEquals(1, call("GET", base + "/tasks" + query, null, 200).get("totalElements").asInt());
        assertEquals(1, call("GET", base + "/tasks" + query + "&active=false", null, 200).get("totalElements").asInt());
        call("DELETE", base, null, 204);
        assertEquals(2, call("GET", base + "/tasks" + query + "&active=all", null, 200).get("totalElements").asInt());
    }

    @Test void rejectsMalformedFiltersAndReversedRanges() throws Exception {
        String path = "/projects/" + project() + "/tasks?";
        for (String query : List.of("status=", "status=pending", "status=0", "type=OTHER", "priority=urgent",
                "labelId=0", "labelId=-1", "labelId=1.0", "labelId=1e0", "labelId=9223372036854775808",
                "dueFrom=2026-02-29", "dueTo=2026-1-01", "dueFrom=0000-01-01", "dueFrom=",
                "dueTo=2026-01-01T00:00:00Z", "dueFrom=2026-02-01&dueTo=2026-01-01")) {
            var problem = call("GET", path + query, null, 400);
            assertEquals("urn:projetotodo:problem:400", problem.get("type").stringValue());
            assertTrue(problem.get("errors").isArray());
        }
        call("GET", "/projects/9223372036854775807/tasks?status=PENDING", null, 404);
    }
}
