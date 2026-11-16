package github.salemby.tododevops;

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
class PostgresProjectTaskIT {
    @Autowired Environment environment;
    @Autowired TaskRepository tasks;
    @Autowired ProjectRepository projects;
    final ObjectMapper json = new ObjectMapper();
    final List<Long> owners = new ArrayList<>();
    JsonNode call(String method, String path, String body, int status) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + environment.getProperty("local.server.port") + path))
            .header("Content-Type", "application/json").method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body)).build();
        var response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
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
        for(String route : List.of("/projects/"+a+"/tasks")) {
            String sep=route.contains("?")?"&":"?";
            for(String filter : List.of("true","false","all")) {
                var rows=call("GET",route+sep+"active="+filter,null,200);
                assertEquals(filter.equals("all")?2:1,rows.size());
                for(var row:rows) assertEquals(a,row.get("projectId").asLong());
            }
        }
        assertEquals(0,call("GET","/projects/"+empty+"/tasks",null,200).size());
        assertEquals(second.get("id"),call("GET","/projects/"+b+"/tasks",null,200).get(0).get("id"));
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
        assertEquals(2,call("GET","/projects/"+a+"/tasks?active=all",null,200).size());
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
        assertEquals(0, call("GET", "/projects/"+b+"/tasks?active=all", null, 200).size());
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
