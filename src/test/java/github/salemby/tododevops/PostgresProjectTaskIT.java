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
class PostgresProjectTaskIT {
    @Autowired Environment environment;
    @Autowired TaskRepository tasks;
    @Autowired ProjectRepository projects;
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
        for (Long id : owners) { tasks.deleteAll(tasks.findAllByProjectId(id)); projects.deleteById(id); }
    }
    @Test void isolatesBacklogsAndRetainsOwnershipAcrossLifecycle() throws Exception {
        long a=project(), b=project(), empty=project();
        var first=task(a); var second=task(b); var archived=task(a);
        long id=first.get("id").asLong(), inactive=archived.get("id").asLong();
        call("DELETE", "/projects/"+a+"/tasks/"+inactive,null,204);
        for (String filter : List.of("true", "false", "all")) {
            var rows = call("GET", "/projects/" + a + "/tasks?active=" + filter, null, 200).get("items");
            assertEquals(filter.equals("all") ? 2 : 1, rows.size());
            for (var row : rows) assertEquals(a, row.get("projectId").asLong());
        }
        assertEquals(0,call("GET","/projects/"+empty+"/tasks",null,200).get("items").size());
        assertEquals(second.get("id"),call("GET","/projects/"+b+"/tasks",null,200).get("items").get(0).get("id"));
        // Extra projectId cannot reassign the task, even if supplied by a caller.
        call("PUT","/projects/"+a+"/tasks/"+id,"{\"projectId\":"+b+",\"title\":\"Updated\",\"status\":\"IN_PROGRESS\"}",200);
        assertEquals(a,call("GET","/projects/"+a+"/tasks/"+id,null,200).get("projectId").asLong());
        var before=call("GET","/projects/"+a+"/tasks/"+id,null,200);
        var inactiveBefore=call("GET","/projects/"+a+"/tasks/"+inactive,null,200);
        call("DELETE","/projects/"+a,null,204);
        assertEquals(before,call("GET","/projects/"+a+"/tasks/"+id,null,200));
        assertEquals(inactiveBefore,call("GET","/projects/"+a+"/tasks/"+inactive,null,200));
        call("POST","/projects/"+a+"/tasks","{\"projectId\":"+a+",\"title\":\"Blocked\"}",409);
        call("PUT","/projects/"+a+"/tasks/"+id,"{\"title\":\"Blocked\",\"status\":\"COMPLETED\"}",409);
        call("DELETE","/projects/"+a+"/tasks/"+id,null,409);
        call("DELETE","/projects/"+a+"/tasks/"+inactive,null,409);
        call("PUT","/projects/"+a+"/tasks/"+inactive,"{\"title\":\"Blocked\",\"status\":\"COMPLETED\"}",409);
        assertEquals(before,call("GET","/projects/"+a+"/tasks/"+id,null,200));
        assertEquals(2,call("GET","/projects/"+a+"/tasks?active=all",null,200).get("items").size());
    }
    @Test void rejectsInvalidAndMissingProjectsWithoutCreatingTasks() throws Exception {
        long count = tasks.count();
        for (String projectId : List.of("0", "-1", "bad", "null", "1.75", "9223372036854775808")) {
            String path = "/projects/"+projectId+"/tasks";
            call("POST", path, "{\"title\":\"Valid\"}", 400);
            call("GET", path, null, 400);
            call("GET", path+"/1", null, 400);
            call("PUT", path+"/1", "{\"title\":\"Valid\",\"status\":\"PENDING\"}", 400);
            call("DELETE", path+"/1", null, 400);
        }
        String missing = "/projects/9223372036854775807/tasks";
        call("POST", missing, "{\"title\":\"Valid\"}", 404);
        call("GET", missing, null, 404);
        call("GET", missing+"/1", null, 404);
        call("PUT", missing+"/1", "{\"title\":\"Valid\",\"status\":\"PENDING\"}", 404);
        call("DELETE", missing+"/1", null, 404);
        long p = project();
        call("GET", "/projects/"+p+"/tasks?active=invalid", null, 400);
        call("GET", "/projects/"+p+"/tasks/9223372036854775807", null, 404);
        assertEquals(count, tasks.count());
    }

    @Test void rejectsFractionalExistingOwnerWithoutCreatingOrChangingTasks() throws Exception {
        long owner = project();
        task(owner);
        String path = "/projects/" + owner + "/tasks";
        var before = call("GET", path + "?active=all", null, 200);
        long count = tasks.count();
        // The integer portion exists: a missing owner must not conceal truncation.
        for (String representation : List.of(owner + ".75", owner + ".0", owner + "e0")) {
            String invalidPath = "/projects/" + representation + "/tasks";
            var response = TestHttp.client().send(TestHttp.request(URI.create("http://127.0.0.1:"
                    + environment.getProperty("local.server.port") + invalidPath))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"title\":\"Must not be inserted\"}")).build(),
                HttpResponse.BodyHandlers.ofString());
            assertEquals(400, response.statusCode(), response.body());
            assertTrue(response.headers().firstValue("Content-Type").orElseThrow().startsWith("application/problem+json"));
            var problem = json.readTree(response.body());
            assertEquals(400, problem.get("status").asInt());
            assertEquals("urn:projetotodo:problem:400", problem.get("type").stringValue());
            assertEquals(invalidPath, problem.get("instance").stringValue());
            assertTrue(problem.get("errors").isArray());
            assertEquals(count, tasks.count());
            assertEquals(before, call("GET", path + "?active=all", null, 200));
        }
        // Ownership comes only from the integer route, even with an obsolete JSON projectId.
        long other = project();
        var valid = call("POST", path, "{\"title\":\"Valid integer owner\",\"projectId\":" + other + ".75}", 201);
        assertEquals(owner, valid.get("projectId").asLong());
        assertEquals(0, tasks.findAllByProjectId(other).size());
        assertEquals(count + 1, tasks.count());
    }

    @Test void requiresProjectForEveryTaskOperation() throws Exception {
        long a = project(), b = project();
        var created = call("POST", "/projects/"+a+"/tasks", "{\"title\":\"Scoped\",\"projectId\":"+b+"}", 201);
        assertEquals(a, created.get("projectId").asLong());
        long id = created.get("id").asLong();
        String own = "/projects/"+a+"/tasks/"+id, other = "/projects/"+b+"/tasks/"+id;
        var before = call("GET", own, null, 200);
        call("GET", other, null, 404);
        call("PUT", other, "{\"title\":\"Wrong owner\",\"status\":\"COMPLETED\"}", 404);
        call("DELETE", other, null, 404);
        assertEquals(before, call("GET", own, null, 200));
        assertEquals(0, call("GET", "/projects/"+b+"/tasks?active=all", null, 200).get("items").size());
    }

    @Test void taskMetadataDefaultsUpdatesAndValidation() throws Exception {
        long p = project();
        String collection = "/projects/" + p + "/tasks";
        var created = call("POST", collection, "{\"title\":\"Metadata\"}", 201);
        assertEquals("TASK", created.get("type").stringValue());
        assertEquals("MEDIUM", created.get("priority").stringValue());
        assertTrue(created.get("dueDate").isNull());
        String path = collection + "/" + created.get("id").asLong();
        for (String type : List.of("TASK", "BUG", "FEATURE", "ENHANCEMENT", "CHORE")) {
            for (String priority : List.of("LOW", "MEDIUM", "HIGH", "CRITICAL")) {
                call("PUT", path, "{\"title\":\"Metadata\",\"status\":\"PENDING\",\"type\":\"" + type
                    + "\",\"priority\":\"" + priority + "\",\"dueDate\":\"2024-02-29\"}", 200);
                var stored = call("GET", path, null, 200);
                assertEquals(type, stored.get("type").stringValue());
                assertEquals(priority, stored.get("priority").stringValue());
                assertEquals("2024-02-29", stored.get("dueDate").stringValue());
            }
        }
        call("PUT", path, "{\"title\":\"Legacy update\",\"status\":\"COMPLETED\"}", 200);
        var preserved = call("GET", path, null, 200);
        assertEquals("CHORE", preserved.get("type").stringValue());
        assertEquals("CRITICAL", preserved.get("priority").stringValue());
        assertEquals("2024-02-29", preserved.get("dueDate").stringValue());
        for (String invalid : List.of("\"type\":null", "\"type\":0", "\"type\":\"chore\"", "\"type\":\"UNKNOWN\"",
                "\"priority\":null", "\"priority\":1", "\"priority\":\"URGENT\"", "\"dueDate\":\"2023-02-29\"",
                "\"dueDate\":\"2026-1-01\"", "\"dueDate\":\"0000-01-01\"", "\"dueDate\":\"2026-01-01T00:00:00Z\"", "\"dueDate\":123")) {
            long count = tasks.count();
            call("POST", collection, "{\"title\":\"Invalid\"," + invalid + "}", 400);
            call("PUT", path, "{\"title\":\"Invalid\",\"status\":\"PENDING\"," + invalid + "}", 400);
            assertEquals(count, tasks.count());
            assertEquals(preserved, call("GET", path, null, 200));
        }
        call("PUT", path, "{\"title\":\"Clear date\",\"status\":\"PENDING\",\"dueDate\":null}", 200);
        assertTrue(call("GET", path, null, 200).get("dueDate").isNull());
        assertEquals("CHORE", call("GET", collection, null, 200).get("items").get(0).get("type").stringValue());
        call("DELETE", path, null, 204);
        assertEquals("CHORE", call("GET", path, null, 200).get("type").stringValue());
    }

    @Test void paginatesWithStableOrderingAndValidatesBoundaries() throws Exception {
        long p = project();
        String path = "/projects/" + p + "/tasks";
        var ids = new ArrayList<Long>();
        for (int i = 0; i < 23; i++) ids.add(task(p).get("id").asLong());
        var first = call("GET", path, null, 200);
        assertEquals(20, first.get("items").size());
        assertEquals(0, first.get("page").asInt());
        assertEquals(20, first.get("size").asInt());
        assertEquals(23, first.get("totalElements").asLong());
        assertEquals(2, first.get("totalPages").asInt());
        for (int i = 0; i < 20; i++) assertEquals(ids.get(i).longValue(), first.get("items").get(i).get("id").asLong());
        var last = call("GET", path + "?page=1", null, 200);
        assertEquals(3, last.get("items").size());
        assertEquals(ids.get(20).longValue(), last.get("items").get(0).get("id").asLong());
        var beyond = call("GET", path + "?page=2", null, 200);
        assertEquals(0, beyond.get("items").size());
        assertEquals(23, beyond.get("totalElements").asLong());
        assertEquals(23, call("GET", path + "?size=100", null, 200).get("items").size());
        assertEquals(1, call("GET", path + "?size=1", null, 200).get("items").size());
        for (String query : List.of("page=-1", "page=1.5", "page=abc", "size=0", "size=-1", "size=101", "size=1.5", "page=2147483647&size=100")) {
            var problem = call("GET", path + "?" + query, null, 400);
            assertEquals(400, problem.get("status").asInt());
            assertTrue(problem.get("errors").isArray());
        }
    }

    @Test void removesGlobalTaskRoutes() throws Exception {
        long a = project();
        for (String route : List.of("/tasks", "/tasks?projectId="+a, "/tasks?active=all", "/tasks/1")) {
            call("GET", route, null, 404);
        }
        call("POST", "/tasks", "{\"title\":\"No global create\",\"projectId\":"+a+"}", 404);
        call("PUT", "/tasks/1", "{\"title\":\"No global update\",\"status\":\"PENDING\"}", 404);
        call("DELETE", "/tasks/1", null, 404);
    }
}
