package github.salemby.tododevops;

import github.salemby.tododevops.support.IsolatedPostgresSchema;
import github.salemby.tododevops.support.TestHttp;
import github.salemby.tododevops.support.TestProjects;
import org.junit.jupiter.api.Test;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Issue #69: managers run a project's work and team; owners keep the project itself and the owners and managers. */
@SpringBootTest(classes = SalemTodoDevOpsApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("postgres-test")
@ContextConfiguration(initializers = IsolatedPostgresSchema.class)
class PostgresManagerRoleIT {
    static final String RANK = "Only an owner can manage owners and managers";
    static final String ROLE_FORBIDDEN = "Your project role does not allow this action";
    static final String NOT_AUTHOR_OR_MODERATOR = "Only the author, an owner or a manager can delete this comment";

    @Autowired Environment environment;
    private final ObjectMapper json = new ObjectMapper();
    private TestHttp.Session owner, manager, member, viewer, outsider;

    @Test
    void managerOperatesTheProject() throws Exception {
        long p = project();
        assertEquals("MANAGER", call(manager, "GET", "/projects/" + p, null, 200).get("role").stringValue());
        call(manager, "POST", "/projects/" + p + "/labels", Map.of("name", "Ops"), 201);
        long t = call(manager, "POST", tasks(p), Map.of("title", "Run it"), 201).get("id").asLong();
        var assigned = call(manager, "PUT", tasks(p) + "/" + t + "/assignee", Map.of("accountId", manager.accountId()), 200);
        assertEquals(manager.accountId(), assigned.get("assigneeId").asLong(), "managers can be assignees");

        long byMember = call(member, "POST", comments(p, t), Map.of("body", "Mine"), 201).get("id").asLong();
        call(manager, "DELETE", comments(p, t) + "/" + byMember, null, 204);
        long byManager = call(manager, "POST", comments(p, t), Map.of("body", "Manager's"), 201).get("id").asLong();
        assertDetail(call(member, "DELETE", comments(p, t) + "/" + byManager, null, 403), NOT_AUTHOR_OR_MODERATOR);
    }

    @Test
    void managerCannotEditOrArchiveTheProject() throws Exception {
        long p = project();
        assertDetail(call(manager, "PUT", "/projects/" + p, Map.of("name", "Renamed"), 403), ROLE_FORBIDDEN);
        assertDetail(call(manager, "DELETE", "/projects/" + p, null, 403), ROLE_FORBIDDEN);
        var project = call(owner, "GET", "/projects/" + p, null, 200);
        assertEquals("Manager fixture", project.get("name").stringValue());
        assertTrue(project.get("active").asBoolean());
    }

    @Test
    void managerManagesMembersAndViewers() throws Exception {
        long p = project();
        var added = call(manager, "POST", members(p), Map.of("email", outsider.email(), "role", "VIEWER"), 201);
        assertEquals("VIEWER", added.get("role").stringValue());
        assertEquals("MEMBER", call(manager, "PUT", members(p) + "/" + outsider.accountId(), Map.of("role", "MEMBER"), 200)
            .get("role").stringValue());
        call(manager, "DELETE", members(p) + "/" + outsider.accountId(), null, 204);
        call(manager, "PUT", members(p) + "/" + viewer.accountId(), Map.of("role", "MEMBER"), 200);
        call(manager, "PUT", members(p) + "/" + viewer.accountId(), Map.of("role", "VIEWER"), 200);
    }

    @Test
    void managerCannotTouchOwnersOrManagers() throws Exception {
        long p = project();
        var manager2 = TestHttp.newSession(uri("/"));
        call(owner, "POST", members(p), Map.of("email", manager2.email(), "role", "MANAGER"), 201);
        var before = roles(p);

        assertDetail(call(manager, "POST", members(p), Map.of("email", outsider.email(), "role", "MANAGER"), 403), RANK);
        assertDetail(call(manager, "POST", members(p), Map.of("email", outsider.email(), "role", "OWNER"), 403), RANK);
        assertDetail(call(manager, "POST", members(p),
            Map.of("email", "nobody-" + UUID.randomUUID() + "@example.com", "role", "OWNER"), 403), RANK);
        assertDetail(call(manager, "PUT", members(p) + "/" + owner.accountId(), Map.of("role", "MEMBER"), 403), RANK);
        assertDetail(call(manager, "PUT", members(p) + "/" + manager2.accountId(), Map.of("role", "MEMBER"), 403), RANK);
        assertDetail(call(manager, "PUT", members(p) + "/" + member.accountId(), Map.of("role", "MANAGER"), 403), RANK);
        assertDetail(call(manager, "PUT", members(p) + "/" + member.accountId(), Map.of("role", "OWNER"), 403), RANK);
        assertDetail(call(manager, "PUT", members(p) + "/" + manager.accountId(), Map.of("role", "OWNER"), 403), RANK);
        assertDetail(call(manager, "DELETE", members(p) + "/" + owner.accountId(), null, 403), RANK);
        assertDetail(call(manager, "DELETE", members(p) + "/" + manager2.accountId(), null, 403), RANK);
        assertEquals(before, roles(p), "refused changes leave every membership as it was");

        assertDetail(call(member, "PUT", members(p) + "/" + viewer.accountId(), Map.of("role", "MEMBER"), 403), ROLE_FORBIDDEN);
        call(manager, "DELETE", members(p) + "/" + manager.accountId(), null, 204);
    }

    @Test
    void ownerManagesManagers() throws Exception {
        long p = project();
        assertEquals("MANAGER", call(owner, "PUT", members(p) + "/" + member.accountId(), Map.of("role", "MANAGER"), 200)
            .get("role").stringValue());
        long t = call(owner, "POST", tasks(p), Map.of("title", "Owned by the manager"), 201).get("id").asLong();
        call(owner, "PUT", tasks(p) + "/" + t + "/assignee", Map.of("accountId", manager.accountId()), 200);
        call(owner, "PUT", members(p) + "/" + manager.accountId(), Map.of("role", "VIEWER"), 200);
        assertTrue(call(owner, "GET", tasks(p) + "/" + t, null, 200).get("assigneeId").isNull(), "a viewer holds no tasks");
        call(owner, "DELETE", members(p) + "/" + member.accountId(), null, 204);

        long q = project(); // owner is its only OWNER, and a MANAGER remains
        assertDetail(call(owner, "DELETE", members(q) + "/" + owner.accountId(), null, 409), "A project must keep at least one active owner");
        assertDetail(call(owner, "PUT", members(q) + "/" + owner.accountId(), Map.of("role", "MANAGER"), 409),
            "A project must keep at least one active owner");
    }

    @Test
    void membersListByRank() throws Exception {
        long p = project();
        var roles = call(owner, "GET", members(p), null, 200).get("items").valueStream()
            .map(m -> m.get("role").stringValue()).toList();
        assertEquals(List.of("OWNER", "MANAGER", "MEMBER", "VIEWER"), roles);
    }

    // --- fixtures -------------------------------------------------------------------------------

    /** A project of the owner with one manager, member and viewer; the accounts are created once per test. */
    private long project() throws Exception {
        if (owner == null) {
            owner = TestHttp.newSession(uri("/"));
            manager = TestHttp.newSession(uri("/"));
            member = TestHttp.newSession(uri("/"));
            viewer = TestHttp.newSession(uri("/"));
            outsider = TestHttp.newSession(uri("/"));
        }
        long p = call(owner, "POST", "/projects", Map.of("name", "Manager fixture", "key", TestProjects.uniqueKey("G")), 201)
            .get("id").asLong();
        call(owner, "POST", members(p), Map.of("email", manager.email(), "role", "MANAGER"), 201);
        call(owner, "POST", members(p), Map.of("email", member.email(), "role", "MEMBER"), 201);
        call(owner, "POST", members(p), Map.of("email", viewer.email(), "role", "VIEWER"), 201);
        return p;
    }

    private List<String> roles(long projectId) throws Exception {
        return call(owner, "GET", members(projectId), null, 200).get("items").valueStream()
            .map(m -> m.get("accountId").asLong() + ":" + m.get("role").stringValue()).toList();
    }

    private JsonNode call(TestHttp.Session caller, String method, String path, Object body, int status) throws Exception {
        var request = caller.request(path).header("Content-Type", "application/json").method(method,
            body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
        var response = TestHttp.client().send(request.build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(status, response.statusCode(), method + " " + path + ": " + response.body());
        return response.body().isEmpty() ? json.nullNode() : json.readTree(response.body());
    }

    private static void assertDetail(JsonNode problem, String detail) {
        assertEquals(detail, problem.get("detail").stringValue(), problem.toString());
    }

    private URI uri(String path) {
        return URI.create("http://127.0.0.1:" + environment.getProperty("local.server.port") + path);
    }

    private static String tasks(long projectId) {
        return "/projects/" + projectId + "/tasks";
    }

    private static String comments(long projectId, long taskId) {
        return tasks(projectId) + "/" + taskId + "/comments";
    }

    private static String members(long projectId) {
        return "/projects/" + projectId + "/members";
    }
}
