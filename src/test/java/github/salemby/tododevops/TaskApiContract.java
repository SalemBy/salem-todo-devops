package github.salemby.tododevops;

import github.salemby.tododevops.workmanagement.domain.Task;
import github.salemby.tododevops.workmanagement.domain.TaskStatus;
import github.salemby.tododevops.workmanagement.infrastructure.persistence.TaskRepository;
import github.salemby.tododevops.workmanagement.application.TaskService;
import github.salemby.tododevops.workmanagement.application.dto.request.CreateTaskRequest;
import github.salemby.tododevops.workmanagement.application.dto.response.TaskResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = SalemTodoDevOpsApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TaskApiContract.FailureConfiguration.class)
abstract class TaskApiContract {    private github.salemby.tododevops.workmanagement.domain.Project owner;
    @Autowired github.salemby.tododevops.workmanagement.infrastructure.persistence.ProjectRepository ownerProjects;
    @org.junit.jupiter.api.BeforeEach
    void createOwner() {
        owner = ownerProjects.saveAndFlush(new github.salemby.tododevops.workmanagement.domain.Project("Task owner", "T" + java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase(), null));
    }
    @org.junit.jupiter.api.AfterEach
    void cleanupOwner() {
        tasks.deleteAll(tasks.findAllByProjectId(owner.getId()));
        ownerProjects.deleteById(owner.getId());
    }
    @Autowired Environment environment;
    @Autowired TaskRepository tasks;

    @TestConfiguration(proxyBeanMethods = false)
    static class FailureConfiguration {
        @Bean
        FailureEndpoint failureEndpoint(TaskService taskService) {
            return new FailureEndpoint(taskService);
        }
    }

    @RestController
    static class FailureEndpoint {
        private final TaskService taskService;

        FailureEndpoint(TaskService taskService) {
            this.taskService = taskService;
        }

        @PostMapping("/test-only/service-validation/{projectId}")
        public TaskResponse createWithoutControllerValidation(@org.springframework.web.bind.annotation.PathVariable Long projectId, @RequestBody CreateTaskRequest request) {
            return taskService.create(projectId, request);
        }

        @GetMapping("/test-only/failure")
        public void fail() {
            throw new IllegalStateException("Sensitive internal database information");
        }
    }

    @Test
    void returnsConsistentApiErrors() throws Exception {
        var invalid = sendRequest("POST", "/projects/" + owner.getId() + "/tasks", "{\"projectId\":" + owner.getId() + ",\"title\":\"   \"}", "application/json");
        var problem = assertProblem(invalid, 400, "/projects/" + owner.getId() + "/tasks");
        assertEquals("Request validation failed", problem.get("detail").asText());
        assertEquals("title", problem.get("errors").get(0).get("field").asText());
        assertEquals("Title is required", problem.get("errors").get(0).get("message").asText());
        assertFalse(invalid.body().contains("rejectedValue"));

        assertProblem(sendRequest("POST", "/projects/" + owner.getId() + "/tasks", "{invalid json}", "application/json"), 400, "/projects/" + owner.getId() + "/tasks");
        assertProblem(sendRequest("POST", "/projects/" + owner.getId() + "/tasks", "", "application/json"), 400, "/projects/" + owner.getId() + "/tasks");
        var enumError = sendRequest("PUT", "/projects/" + owner.getId() + "/tasks/1",
            "{\"title\":\"Valid\",\"status\":\"PRIVATE_VALUE\"}", "application/json");
        assertProblem(enumError, 400, "/projects/" + owner.getId() + "/tasks/1");
        assertFalse(enumError.body().contains("PRIVATE_VALUE"));
        assertProblem(sendRequest("DELETE", "/projects/" + owner.getId() + "/tasks/not-a-number", null, null), 400, "/projects/" + owner.getId() + "/tasks/not-a-number");
        var missing = assertProblem(deleteTask(Long.MAX_VALUE), 404, "/projects/" + owner.getId() + "/tasks/" + Long.MAX_VALUE);
        assertEquals("Task not found", missing.get("detail").asText());
        assertProblem(getTasks("?active=invalid"), 400, "/projects/" + owner.getId() + "/tasks");
        assertProblem(sendRequest("GET", "/unknown-route", null, null), 404, "/unknown-route");
        var methodError = sendRequest("PATCH", "/projects/" + owner.getId() + "/tasks", "{}", "application/json");
        assertProblem(methodError, 405, "/projects/" + owner.getId() + "/tasks");
        assertTrue(methodError.headers().firstValue("Allow").orElseThrow().contains("POST"));
        assertProblem(sendRequest("POST", "/projects/" + owner.getId() + "/tasks", "text", "text/plain"), 415, "/projects/" + owner.getId() + "/tasks");
    }

    @Test
    void serviceValidationStillReturnsFieldErrorsWithoutControllerValid() throws Exception {
        long count = tasks.count();
        var response = sendRequest("POST", "/test-only/service-validation/" + owner.getId(), "{\"projectId\":" + owner.getId() + ",\"title\":\" \"}", "application/json");
        var problem = assertProblem(response, 400, "/test-only/service-validation/" + owner.getId());
        assertEquals("title", problem.get("errors").get(0).get("field").asText());
        assertEquals("Title is required", problem.get("errors").get(0).get("message").asText());
        assertEquals(count, tasks.count());
    }

    @Test
    void hidesUnexpectedFailureDetails() throws Exception {
        var response = sendRequest("GET", "/test-only/failure", null, null);
        var problem = assertProblem(response, 500, "/test-only/failure");
        assertEquals("An unexpected error occurred", problem.get("detail").asText());
        assertFalse(response.body().contains("Sensitive"));
        assertFalse(response.body().contains("IllegalStateException"));
        assertFalse(response.body().contains("trace"));
    }

    private tools.jackson.databind.JsonNode assertProblem(HttpResponse<String> response, int status, String path) {
        assertEquals(status, response.statusCode(), response.body());
        assertTrue(response.headers().firstValue("Content-Type").orElseThrow()
            .startsWith("application/problem+json"));
        var problem = new tools.jackson.databind.ObjectMapper().readTree(response.body());
        assertEquals(status, problem.get("status").asInt());
        assertEquals("urn:projetotodo:problem:" + status, problem.path("type").asText(), response.body());
        assertFalse(problem.get("title").asText().isBlank());
        assertFalse(problem.get("detail").asText().isBlank());
        assertEquals(path, problem.get("instance").asText());
        assertTrue(problem.get("errors").isArray());
        return problem;
    }

    private HttpResponse<String> sendRequest(String method, String path, String body, String contentType) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:"
            + environment.getProperty("local.server.port") + path));
        if (contentType != null) {
            builder.header("Content-Type", contentType);
        }
        var request = builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
            : HttpRequest.BodyPublishers.ofString(body)).build();
        return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void servesHttp() throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:"
            + environment.getProperty("local.server.port") + "/")).GET().build();
        var response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("running"));
    }

    @Test
    void listsProjectTasksThroughHttp() throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:"
            + environment.getProperty("local.server.port") + "/projects/" + owner.getId() + "/tasks")).GET().build();
        var client = HttpClient.newHttpClient();
        var emptyResponse = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, emptyResponse.statusCode());
        assertEquals("[]", emptyResponse.body());

        Task first = new Task(owner);
        first.setTitle("First listed task");
        Task second = new Task(owner);
        second.setTitle("Second listed task");
        var saved = tasks.saveAllAndFlush(java.util.List.of(first, second));
        try {
            var response = client.send(request, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, response.statusCode());
            assertTrue(response.headers().firstValue("Content-Type").orElseThrow()
                .startsWith("application/json"));
            var json = new tools.jackson.databind.ObjectMapper().readTree(response.body());
            assertTrue(json.isArray());
            assertEquals(2, json.size());
            var ids = new java.util.HashSet<Long>();
            for (var item : json) {
                ids.add(item.get("id").asLong());
                assertEquals("PENDING", item.get("status").asText());
                assertFalse(item.get("createdAt").isNull());
            }
            assertEquals(java.util.Set.of(first.getId(), second.getId()), ids);
        } finally {
            tasks.deleteAll(saved);
        }
    }

    @Test
    void createsTaskThroughHttp() throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:"
            + environment.getProperty("local.server.port") + "/projects/" + owner.getId() + "/tasks"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(
                "{\"projectId\":" + owner.getId() + ",\"title\":\"Created through HTTP\",\"description\":\"Task details\"}"))
            .build();
        var response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(201, response.statusCode());
        String location = response.headers().firstValue("Location").orElseThrow();
        assertTrue(location.contains("/projects/" + owner.getId() + "/tasks/"));
        Long id = Long.valueOf(location.substring(location.lastIndexOf('/') + 1));
        try {
            Task saved = tasks.findById(id).orElseThrow();
            assertEquals("Created through HTTP", saved.getTitle());
            assertEquals("Task details", saved.getDescription());
            assertEquals(TaskStatus.PENDING, saved.getStatus());
            assertTrue(response.body().contains("\"status\":\"PENDING\""));
        } finally {
            tasks.deleteById(id);
        }
    }

    @Test
    void rejectsInvalidTasksThroughHttp() throws Exception {
        long count = tasks.count();
        var client = HttpClient.newHttpClient();
        for (String body : new String[] {
            "{}",
            "{\"title\":\"   \"}",
            "{\"title\":\"" + "a".repeat(201) + "\"}",
            "{\"title\":\"Valid\",\"description\":\"" + "a".repeat(2001) + "\"}",
            "{invalid json}"
        }) {
            var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:"
                + environment.getProperty("local.server.port") + "/projects/" + owner.getId() + "/tasks"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build();
            var response = client.send(request, HttpResponse.BodyHandlers.ofString());
            assertEquals(400, response.statusCode());
        }
        assertEquals(count, tasks.count());
    }

    @Test
    void updatesTaskThroughHttp() throws Exception {
        Task task = new Task(owner);
        task.setTitle("Original");
        task.setDescription("Original details");
        Task saved = tasks.saveAndFlush(task);
        Task original = tasks.findById(saved.getId()).orElseThrow();
        try {
            var response = putTask(saved.getId(),
                "{\"title\":\"Updated\",\"description\":\"New details\",\"status\":\"COMPLETED\"}");
            assertEquals(200, response.statusCode());
            Task updated = tasks.findById(saved.getId()).orElseThrow();
            assertEquals("Updated", updated.getTitle());
            assertEquals("New details", updated.getDescription());
            assertEquals(TaskStatus.COMPLETED, updated.getStatus());
            assertEquals(original.getCreatedAt(), updated.getCreatedAt());
            assertTrue(updated.getUpdatedAt().isAfter(original.getUpdatedAt()));
            var json = new tools.jackson.databind.ObjectMapper().readTree(response.body());
            assertEquals(saved.getId().longValue(), json.get("id").asLong());
            assertEquals("Updated", json.get("title").asText());
            assertEquals("COMPLETED", json.get("status").asText());
            assertTrue(java.time.Duration.between(updated.getUpdatedAt(),
                java.time.Instant.parse(json.get("updatedAt").asText())).abs().toNanos() < 1000);

            assertEquals(200, putTask(saved.getId(),
                "{\"title\":\"Updated\",\"status\":\"PENDING\"}").statusCode());
            assertNull(tasks.findById(saved.getId()).orElseThrow().getDescription());
        } finally {
            tasks.deleteById(saved.getId());
        }
    }

    @Test
    void rejectsInvalidUpdatesAndMissingTasks() throws Exception {
        Task task = new Task(owner);
        task.setTitle("Original");
        Task saved = tasks.saveAndFlush(task);
        try {
            for (String body : new String[] {
                "{}", "null", "{invalid json}",
                "{\"title\":\"   \",\"status\":\"PENDING\"}",
                "{\"title\":\"" + "a".repeat(201) + "\",\"status\":\"PENDING\"}",
                "{\"title\":\"Valid\",\"description\":\"" + "a".repeat(2001) + "\",\"status\":\"PENDING\"}",
                "{\"title\":\"Valid\"}",
                "{\"title\":\"Valid\",\"status\":\"UNKNOWN\"}"
            }) {
                assertEquals(400, putTask(saved.getId(), body).statusCode());
            }
            assertEquals("Original", tasks.findById(saved.getId()).orElseThrow().getTitle());
            long count = tasks.count();
            assertEquals(404, putTask(Long.MAX_VALUE,
                "{\"title\":\"Valid\",\"status\":\"PENDING\"}").statusCode());
            assertEquals(count, tasks.count());
        } finally {
            tasks.deleteById(saved.getId());
        }
    }

    private HttpResponse<String> putTask(Long id, String body) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:"
            + environment.getProperty("local.server.port") + "/projects/" + owner.getId() + "/tasks/" + id))
            .header("Content-Type", "application/json")
            .PUT(HttpRequest.BodyPublishers.ofString(body)).build();
        return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void softDeletesAndFiltersTasksThroughHttp() throws Exception {
        Task first = new Task(owner);
        first.setTitle("Keep active");
        Task second = new Task(owner);
        second.setTitle("Deactivate");
        var saved = tasks.saveAllAndFlush(java.util.List.of(first, second));
        var original = tasks.findById(second.getId()).orElseThrow();
        long count = tasks.count();
        try {
            assertTrue(original.isActive());
            var response = deleteTask(second.getId());
            assertEquals(204, response.statusCode());
            assertEquals("", response.body());
            Task inactive = tasks.findById(second.getId()).orElseThrow();
            assertFalse(inactive.isActive());
            assertEquals(original.getTitle(), inactive.getTitle());
            assertEquals(original.getCreatedAt(), inactive.getCreatedAt());
            assertTrue(inactive.getUpdatedAt().isAfter(original.getUpdatedAt()));
            assertEquals(count, tasks.count());

            assertTaskIds("", first.getId());
            assertTaskIds("?active=true", first.getId());
            assertTaskIds("?active=false", second.getId());
            assertTaskIds("?active=all", first.getId(), second.getId());
            assertEquals(400, getTasks("?active=invalid").statusCode());
            assertEquals(204, deleteTask(second.getId()).statusCode());
            assertEquals(404, deleteTask(Long.MAX_VALUE).statusCode());
            assertEquals(404, putTask(second.getId(),
                "{\"title\":\"Changed\",\"status\":\"COMPLETED\"}").statusCode());
            assertEquals(original.getTitle(), tasks.findById(second.getId()).orElseThrow().getTitle());
            assertFalse(tasks.findById(second.getId()).orElseThrow().isActive());
        } finally {
            tasks.deleteAll(saved);
        }
    }

    private HttpResponse<String> deleteTask(Long id) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:"
            + environment.getProperty("local.server.port") + "/projects/" + owner.getId() + "/tasks/" + id)).DELETE().build();
        return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> getTasks(String query) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:"
            + environment.getProperty("local.server.port") + "/projects/" + owner.getId() + "/tasks" + query)).GET().build();
        return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
    }

    private void assertTaskIds(String query, Long... expected) throws Exception {
        var response = getTasks(query);
        assertEquals(200, response.statusCode());
        var json = new tools.jackson.databind.ObjectMapper().readTree(response.body());
        assertTrue(json.isArray());
        var ids = new java.util.HashSet<Long>();
        for (var item : json) {
            ids.add(item.get("id").asLong());
            boolean active = tasks.findById(item.get("id").asLong()).orElseThrow().isActive();
            assertEquals(active, item.get("active").asBoolean());
        }
        assertEquals(expected.length, json.size());
        assertEquals(java.util.Set.of(expected), ids);
    }

    @Test
    void persistsTask() {
        Task task = new Task(owner);
        task.setTitle("My first task");
        Task saved = tasks.saveAndFlush(task);
        Task loaded = tasks.findById(saved.getId()).orElseThrow();
        assertEquals("My first task", loaded.getTitle());
        assertEquals(TaskStatus.PENDING, loaded.getStatus());
        assertNotNull(loaded.getCreatedAt());
        assertNotNull(loaded.getUpdatedAt());
        tasks.deleteById(saved.getId());
    }
}
