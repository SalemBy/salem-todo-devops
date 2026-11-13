package github.salemby.projetotodo;

import github.salemby.projetotodo.entity.Task;
import github.salemby.projetotodo.enums.TaskStatus;
import github.salemby.projetotodo.repository.TaskRepository;
import github.salemby.projetotodo.service.TaskService;
import github.salemby.projetotodo.dto.request.CreateTaskRequest;
import github.salemby.projetotodo.dto.response.TaskResponse;
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

@SpringBootTest(classes = ProjetoTodoApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TaskApiContract.FailureConfiguration.class)
abstract class TaskApiContract {
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

        @PostMapping("/test-only/service-validation")
        public TaskResponse createWithoutControllerValidation(@RequestBody CreateTaskRequest request) {
            return taskService.create(request);
        }

        @GetMapping("/test-only/failure")
        public void fail() {
            throw new IllegalStateException("Sensitive internal database information");
        }
    }

    @Test
    void returnsConsistentApiErrors() throws Exception {
        var invalid = sendRequest("POST", "/tasks", "{\"title\":\"   \"}", "application/json");
        var problem = assertProblem(invalid, 400, "/tasks");
        assertEquals("Request validation failed", problem.get("detail").asText());
        assertEquals("title", problem.get("errors").get(0).get("field").asText());
        assertEquals("Title is required", problem.get("errors").get(0).get("message").asText());
        assertFalse(invalid.body().contains("rejectedValue"));

        assertProblem(sendRequest("POST", "/tasks", "{invalid json}", "application/json"), 400, "/tasks");
        assertProblem(sendRequest("POST", "/tasks", "", "application/json"), 400, "/tasks");
        var enumError = sendRequest("PUT", "/tasks/1",
            "{\"title\":\"Valid\",\"status\":\"PRIVATE_VALUE\"}", "application/json");
        assertProblem(enumError, 400, "/tasks/1");
        assertFalse(enumError.body().contains("PRIVATE_VALUE"));
        assertProblem(sendRequest("DELETE", "/tasks/not-a-number", null, null), 400, "/tasks/not-a-number");
        var missing = assertProblem(deleteTask(Long.MAX_VALUE), 404, "/tasks/" + Long.MAX_VALUE);
        assertEquals("Task not found", missing.get("detail").asText());
        assertProblem(getTasks("?active=invalid"), 400, "/tasks");
        assertProblem(sendRequest("GET", "/unknown-route", null, null), 404, "/unknown-route");
        var methodError = sendRequest("PATCH", "/tasks", "{}", "application/json");
        assertProblem(methodError, 405, "/tasks");
        assertTrue(methodError.headers().firstValue("Allow").orElseThrow().contains("POST"));
        assertProblem(sendRequest("POST", "/tasks", "text", "text/plain"), 415, "/tasks");
    }

    @Test
    void serviceValidationStillReturnsFieldErrorsWithoutControllerValid() throws Exception {
        long count = tasks.count();
        var response = sendRequest("POST", "/test-only/service-validation", "{\"title\":\" \"}", "application/json");
        var problem = assertProblem(response, 400, "/test-only/service-validation");
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
    void listsAllTasksThroughHttp() throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:"
            + environment.getProperty("local.server.port") + "/tasks")).GET().build();
        var client = HttpClient.newHttpClient();
        var emptyResponse = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, emptyResponse.statusCode());
        assertEquals("[]", emptyResponse.body());

        Task first = new Task();
        first.setTitle("First listed task");
        Task second = new Task();
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
            + environment.getProperty("local.server.port") + "/tasks"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(
                "{\"title\":\"Created through HTTP\",\"description\":\"Task details\"}"))
            .build();
        var response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(201, response.statusCode());
        String location = response.headers().firstValue("Location").orElseThrow();
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
                + environment.getProperty("local.server.port") + "/tasks"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build();
            var response = client.send(request, HttpResponse.BodyHandlers.ofString());
            assertEquals(400, response.statusCode());
        }
        assertEquals(count, tasks.count());
    }

    @Test
    void updatesTaskThroughHttp() throws Exception {
        Task task = new Task();
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
        Task task = new Task();
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
            + environment.getProperty("local.server.port") + "/tasks/" + id))
            .header("Content-Type", "application/json")
            .PUT(HttpRequest.BodyPublishers.ofString(body)).build();
        return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void softDeletesAndFiltersTasksThroughHttp() throws Exception {
        Task first = new Task();
        first.setTitle("Keep active");
        Task second = new Task();
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
            + environment.getProperty("local.server.port") + "/tasks/" + id)).DELETE().build();
        return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> getTasks(String query) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:"
            + environment.getProperty("local.server.port") + "/tasks" + query)).GET().build();
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
        Task task = new Task();
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
