package github.salemby.tododevops;

import github.salemby.tododevops.support.IsolatedPostgresSchema;
import github.salemby.tododevops.support.TaskRows;
import github.salemby.tododevops.support.TestHttp;
import github.salemby.tododevops.support.TestProjects;
import github.salemby.tododevops.workmanagement.domain.ProjectMembership;
import github.salemby.tododevops.workmanagement.domain.ProjectRole;
import github.salemby.tododevops.workmanagement.infrastructure.persistence.LabelRepository;
import github.salemby.tododevops.workmanagement.infrastructure.persistence.ProjectMembershipRepository;
import github.salemby.tododevops.workmanagement.infrastructure.persistence.ProjectRepository;
import github.salemby.tododevops.workmanagement.infrastructure.persistence.TaskRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;

/** Issue #9: every project, task, and label endpoint enforces membership permissions. */
@SpringBootTest(classes = SalemTodoDevOpsApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("postgres-test")
@ContextConfiguration(initializers = IsolatedPostgresSchema.class)
class PostgresProjectAccessIT {
    enum Caller { OWNER, MANAGER, MEMBER, VIEWER, OUTSIDER }

    private record Fixture(long project, long task, long label) {
        String path() { return "/projects/" + project; }
    }

    /** One set of logged-in accounts per isolated server; logging in is deliberately slow. */
    private static final Map<String, Map<Caller, TestHttp.Session>> SESSIONS = new ConcurrentHashMap<>();

    @Autowired Environment environment;
    @Autowired ProjectRepository projects;
    @Autowired ProjectMembershipRepository memberships;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
    @Autowired TaskRepository tasks;
    @Autowired LabelRepository labels;
    private final ObjectMapper json = new ObjectMapper();

    @ParameterizedTest(name = "{0}")
    @EnumSource(Caller.class)
    void enforcesThePermissionMatrixOnEveryEndpoint(Caller caller) throws Exception {
        var f = fixture();
        var p = f.path();
        int read = caller == Caller.OUTSIDER ? 404 : 200;
        for (String path : List.of(p, p + "/tasks", p + "/tasks/" + f.task(), p + "/tasks/" + f.task() + "/labels",
                p + "/labels", p + "/labels/" + f.label(), p + "/members")) {
            expect(caller, "GET", path, null, read);
        }

        boolean writer = caller == Caller.OWNER || caller == Caller.MANAGER || caller == Caller.MEMBER;
        int denied = caller == Caller.OUTSIDER ? 404 : 403;
        String task = p + "/tasks/" + f.task(), label = p + "/labels/" + f.label();
        expect(caller, "POST", p + "/tasks", "{\"title\":\"Created by caller\"}", writer ? 201 : denied);
        expect(caller, "PUT", task, "{\"title\":\"Changed\",\"status\":\"IN_PROGRESS\"}", writer ? 200 : denied);
        expect(caller, "PUT", task + "/labels/" + f.label(), null, writer ? 204 : denied);
        expect(caller, "DELETE", task + "/labels/" + f.label(), null, writer ? 204 : denied);
        expect(caller, "POST", p + "/labels", "{\"name\":\"Caller label\"}", writer ? 201 : denied);
        expect(caller, "PUT", label, "{\"name\":\"Renamed label\"}", writer ? 200 : denied);
        expect(caller, "DELETE", label, null, writer ? 204 : denied);
        expect(caller, "DELETE", task, null, writer ? 204 : denied);

        boolean manager = caller == Caller.OWNER; // only owners edit or archive the project; MANAGERs cannot (#69)
        expect(caller, "PUT", p, "{\"name\":\"Renamed project\"}", manager ? 200 : denied);
        expect(caller, "DELETE", p, null, manager ? 204 : denied);

        if (!writer) {
            var unchangedTask = tasks.findById(f.task()).orElseThrow();
            assertEquals("Fixture task", unchangedTask.getTitle());
            assertTrue(unchangedTask.isActive());
            assertTrue(labels.findById(f.label()).orElseThrow().isActive());
            assertEquals(1, TaskRows.count(jdbc, f.project()), "Denied callers must not create tasks");
        }
        if (!manager) {
            var unchangedProject = projects.findById(f.project()).orElseThrow();
            assertEquals("Access fixture", unchangedProject.getName());
            assertTrue(unchangedProject.isActive());
        }
    }

    @Test
    void listsOnlyTheCallersProjectsWithTheirRole() throws Exception {
        var f = fixture();
        for (Caller caller : Caller.values()) {
            JsonNode listed = null;
            var response = send(caller, "GET", "/projects?active=all", null);
            assertEquals(200, response.statusCode());
            for (JsonNode item : json.readTree(response.body())) {
                if (item.get("id").asLong() == f.project()) listed = item;
            }
            if (caller == Caller.OUTSIDER) {
                assertNull(listed, "Non-members must not see the project");
            } else {
                assertNotNull(listed, caller + " must see the project");
                assertEquals(caller.name(), listed.get("role").stringValue());
                assertEquals(caller.name(), json.readTree(send(caller, "GET", f.path(), null).body()).get("role").stringValue());
            }
        }
    }

    @Test
    void checksPermissionBeforeProjectState() throws Exception {
        var f = fixture();
        expect(Caller.OWNER, "DELETE", f.path(), null, 204);
        String task = f.path() + "/tasks/" + f.task();
        String update = "{\"title\":\"Changed\",\"status\":\"PENDING\"}";
        expect(Caller.VIEWER, "PUT", task, update, 403);
        expect(Caller.OUTSIDER, "PUT", task, update, 404);
        expect(Caller.MEMBER, "PUT", task, update, 409);
        expect(Caller.VIEWER, "GET", task, null, 200);
    }

    @Test
    void nonMembersCannotTellAProjectExists() throws Exception {
        var f = fixture();
        var hidden = send(Caller.OUTSIDER, "GET", f.path(), null);
        var missing = send(Caller.OUTSIDER, "GET", "/projects/9223372036854775807", null);
        assertEquals(404, hidden.statusCode());
        assertEquals(missing.statusCode(), hidden.statusCode());
        assertEquals(json.readTree(missing.body()).get("detail"), json.readTree(hidden.body()).get("detail"));
        assertEquals(json.readTree(missing.body()).get("title"), json.readTree(hidden.body()).get("title"));
    }

    @Test
    void rejectsMalformedRequestsBeforeCheckingAccess() throws Exception {
        var f = fixture();
        expect(Caller.OUTSIDER, "GET", f.path() + "/tasks?active=bogus", null, 400);
        expect(Caller.OUTSIDER, "GET", f.path() + "/tasks?page=-1", null, 400);
        expect(Caller.OUTSIDER, "GET", f.path() + "/labels?active=bogus", null, 400);
        expect(Caller.OUTSIDER, "POST", f.path() + "/tasks", "{}", 400);
        expect(Caller.OUTSIDER, "GET", "/projects/0", null, 400);
    }

    /** A fresh project owned by OWNER, with MANAGER, MEMBER and VIEWER memberships, one task, and one label. */
    private Fixture fixture() throws Exception {
        var owner = session(Caller.OWNER);
        var project = TestProjects.owned(projects, memberships, owner.accountId(), "Access fixture",
            TestProjects.uniqueKey("A"));
        memberships.saveAndFlush(new ProjectMembership(project, session(Caller.MANAGER).accountId(), ProjectRole.MANAGER));
        memberships.saveAndFlush(new ProjectMembership(project, session(Caller.MEMBER).accountId(), ProjectRole.MEMBER));
        memberships.saveAndFlush(new ProjectMembership(project, session(Caller.VIEWER).accountId(), ProjectRole.VIEWER));
        String p = "/projects/" + project.getId();
        long task = id(send(Caller.OWNER, "POST", p + "/tasks", "{\"title\":\"Fixture task\"}"));
        long label = id(send(Caller.OWNER, "POST", p + "/labels", "{\"name\":\"Fixture label\"}"));
        return new Fixture(project.getId(), task, label);
    }

    private void expect(Caller caller, String method, String path, String body, int status) throws Exception {
        var response = send(caller, method, path, body);
        assertEquals(status, response.statusCode(), caller + " " + method + " " + path + ": " + response.body());
        if (status >= 400) {
            assertTrue(response.headers().firstValue("Content-Type").orElseThrow().startsWith("application/problem+json"));
            assertEquals(status, json.readTree(response.body()).get("status").asInt());
        }
    }

    private HttpResponse<String> send(Caller caller, String method, String path, String body) throws Exception {
        var builder = session(caller).request(path);
        if (body != null) builder.header("Content-Type", "application/json");
        return TestHttp.client().send(builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
            : HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }

    private long id(HttpResponse<String> response) {
        assertEquals(201, response.statusCode(), response.body());
        return json.readTree(response.body()).get("id").asLong();
    }

    private TestHttp.Session session(Caller caller) {
        var server = URI.create("http://127.0.0.1:" + environment.getProperty("local.server.port"));
        return SESSIONS.computeIfAbsent(server.toString(), ignored -> {
            var sessions = new EnumMap<Caller, TestHttp.Session>(Caller.class);
            for (Caller each : Caller.values()) sessions.put(each, TestHttp.newSession(server));
            return sessions;
        }).get(caller);
    }
}
