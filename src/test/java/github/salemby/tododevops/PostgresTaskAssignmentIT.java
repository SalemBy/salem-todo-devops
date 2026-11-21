package github.salemby.tododevops;

import github.salemby.tododevops.support.IsolatedPostgresSchema;
import github.salemby.tododevops.support.TestHttp;
import github.salemby.tododevops.support.TestProjects;
import github.salemby.tododevops.workmanagement.domain.ProjectMembership;
import github.salemby.tododevops.workmanagement.domain.ProjectRole;
import github.salemby.tododevops.workmanagement.infrastructure.persistence.ProjectMembershipRepository;
import github.salemby.tododevops.workmanagement.infrastructure.persistence.ProjectRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;

/** Issue #10: one optional assignee per task, chosen from the project's members who may write tasks. */
@SpringBootTest(classes = SalemTodoDevOpsApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("postgres-test")
@ContextConfiguration(initializers = IsolatedPostgresSchema.class)
class PostgresTaskAssignmentIT {
    static final String INVALID_ASSIGNEE = "Assignee must be an active owner or member of this project";

    /** OWNER owns each fixture project; SECOND is a MEMBER, THIRD a VIEWER, OUTSIDER nothing, DORMANT a MEMBER. */
    enum Account { OWNER, SECOND, THIRD, OUTSIDER, DORMANT }

    private static final Map<String, Map<Account, TestHttp.Session>> SESSIONS = new ConcurrentHashMap<>();

    @Autowired Environment environment;
    @Autowired ProjectRepository projects;
    @Autowired ProjectMembershipRepository memberships;
    @Autowired JdbcTemplate jdbc;
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void ownerAssignsAMemberAndEveryReadShowsIt() throws Exception {
        long p = project();
        long t = task(p);
        var assigned = json.readTree(expect(Account.OWNER, "PUT", assignee(p, t), body(Account.SECOND), 200).body());
        assertEquals(id(Account.SECOND), assigned.get("assigneeId").asLong());
        assertEquals(id(Account.SECOND), read(Account.THIRD, p, t).get("assigneeId").asLong());
        var list = json.readTree(expect(Account.THIRD, "GET", tasks(p), null, 200).body());
        assertEquals(id(Account.SECOND), list.get("items").get(0).get("assigneeId").asLong());
        // A task update without assigneeId keeps the assignee.
        var updated = json.readTree(expect(Account.OWNER, "PUT", tasks(p) + "/" + t,
            "{\"title\":\"Renamed\",\"status\":\"IN_PROGRESS\"}", 200).body());
        assertEquals(id(Account.SECOND), updated.get("assigneeId").asLong());
    }

    @Test
    void memberAssignsThemselves() throws Exception {
        long p = project();
        long t = task(p);
        var assigned = json.readTree(expect(Account.SECOND, "PUT", assignee(p, t), body(Account.SECOND), 200).body());
        assertEquals(id(Account.SECOND), assigned.get("assigneeId").asLong());
    }

    @Test
    void unassignClearsAndIsIdempotent() throws Exception {
        long p = project();
        long t = task(p);
        expect(Account.OWNER, "PUT", assignee(p, t), body(Account.SECOND), 200);
        expect(Account.SECOND, "DELETE", assignee(p, t), null, 204);
        expect(Account.SECOND, "DELETE", assignee(p, t), null, 204);
        assertTrue(read(Account.OWNER, p, t).get("assigneeId").isNull());
    }

    @Test
    void invalidAssigneesAreRejectedWithoutChanges() throws Exception {
        long p = project();
        long t = task(p);
        expect(Account.OWNER, "PUT", assignee(p, t), body(Account.SECOND), 200);
        jdbc.update("update accounts set active = false where id = ?", id(Account.DORMANT));
        try {
            for (String target : List.of(body(Account.THIRD), body(Account.OUTSIDER), body(Account.DORMANT),
                    "{\"accountId\":999999999}")) {
                var response = expect(Account.OWNER, "PUT", assignee(p, t), target, 422);
                assertEquals(INVALID_ASSIGNEE, json.readTree(response.body()).get("detail").stringValue(), target);
                assertEquals(id(Account.SECOND), read(Account.OWNER, p, t).get("assigneeId").asLong(), target);
            }
        } finally {
            jdbc.update("update accounts set active = true where id = ?", id(Account.DORMANT));
        }
    }

    @Test
    void invalidBodiesAre400() throws Exception {
        long p = project();
        long t = task(p);
        for (String invalid : List.of("{}", "{\"accountId\":0}", "{\"accountId\":-1}", "{\"accountId\":null}",
                "{\"accountId\":\"" + id(Account.SECOND) + "\"}", "{\"accountId\":42.5}", "not json"))
            expect(Account.OWNER, "PUT", assignee(p, t), invalid, 400);
        assertTrue(read(Account.OWNER, p, t).get("assigneeId").isNull());
    }

    @Test
    void permissionsAndLifecycle() throws Exception {
        long p = project();
        long t = task(p);
        expect(Account.THIRD, "PUT", assignee(p, t), body(Account.SECOND), 403);
        expect(Account.THIRD, "DELETE", assignee(p, t), null, 403);
        expect(Account.OUTSIDER, "PUT", assignee(p, t), body(Account.SECOND), 404);

        long other = project();
        long elsewhere = task(other);
        expect(Account.OWNER, "PUT", assignee(p, elsewhere), body(Account.SECOND), 404);

        expect(Account.OWNER, "PUT", assignee(p, t), body(Account.SECOND), 200);
        String stored = read(Account.OWNER, p, t).get("updatedAt").stringValue();
        expect(Account.OWNER, "PUT", assignee(p, t), body(Account.SECOND), 200);
        assertEquals(stored, read(Account.OWNER, p, t).get("updatedAt").stringValue(),
            "assigning the current assignee changes nothing");

        expect(Account.OWNER, "DELETE", tasks(p) + "/" + t, null, 204);
        expect(Account.OWNER, "PUT", assignee(p, t), body(Account.OWNER), 404);
        expect(Account.OWNER, "DELETE", assignee(p, t), null, 404);
        assertEquals(id(Account.SECOND), read(Account.OWNER, p, t).get("assigneeId").asLong(), "archived tasks keep it");

        expect(Account.OWNER, "DELETE", "/projects/" + p, null, 204);
        expect(Account.OWNER, "PUT", assignee(p, t), body(Account.OWNER), 409);
        expect(Account.OWNER, "DELETE", assignee(p, t), null, 409);
    }

    @Test
    void removingTheAssigneeClearsTheirTasksInThatProjectOnly() throws Exception {
        long a = project(), b = project();
        long inA = task(a), inB = task(b);
        expect(Account.OWNER, "PUT", assignee(a, inA), body(Account.SECOND), 200);
        expect(Account.OWNER, "PUT", assignee(b, inB), body(Account.SECOND), 200);
        String before = read(Account.OWNER, a, inA).get("updatedAt").stringValue();

        expect(Account.OWNER, "DELETE", members(a, Account.SECOND), null, 204);

        var cleared = read(Account.OWNER, a, inA);
        assertTrue(cleared.get("assigneeId").isNull());
        assertTrue(cleared.get("updatedAt").stringValue().compareTo(before) > 0, "clearing is a change to the task");
        assertEquals(id(Account.SECOND), read(Account.OWNER, b, inB).get("assigneeId").asLong(), "other projects keep it");
    }

    @Test
    void leavingClearsOwnAssignments() throws Exception {
        long p = project();
        long t = task(p);
        expect(Account.SECOND, "PUT", assignee(p, t), body(Account.SECOND), 200);
        expect(Account.SECOND, "DELETE", members(p, Account.SECOND), null, 204);
        assertTrue(read(Account.OWNER, p, t).get("assigneeId").isNull());
    }

    @Test
    void demotionToViewerClearsButOwnerToMemberKeeps() throws Exception {
        long p = project();
        long t = task(p);
        expect(Account.OWNER, "PUT", members(p, Account.SECOND), "{\"role\":\"OWNER\"}", 200);
        expect(Account.OWNER, "PUT", assignee(p, t), body(Account.SECOND), 200);

        expect(Account.OWNER, "PUT", members(p, Account.SECOND), "{\"role\":\"MEMBER\"}", 200);
        assertEquals(id(Account.SECOND), read(Account.OWNER, p, t).get("assigneeId").asLong(), "MEMBER may still write tasks");

        expect(Account.OWNER, "PUT", members(p, Account.SECOND), "{\"role\":\"VIEWER\"}", 200);
        assertTrue(read(Account.OWNER, p, t).get("assigneeId").isNull(), "a VIEWER cannot hold a task");
    }

    @Test
    void concurrentAssignAndRemoveLeaveNoDanglingAssignee() throws Exception {
        long p = project();
        long t = task(p);
        var pool = Executors.newFixedThreadPool(2);
        try {
            for (int round = 0; round < 5; round++) {
                if (memberships.findByProjectIdAndAccountId(p, id(Account.SECOND)).isEmpty())
                    member(p, Account.SECOND, ProjectRole.MEMBER);
                var start = new CountDownLatch(1);
                var assign = pool.submit(() -> { start.await(); return send(Account.OWNER, "PUT", assignee(p, t), body(Account.SECOND)); });
                var remove = pool.submit(() -> { start.await(); return send(Account.OWNER, "DELETE", members(p, Account.SECOND), null); });
                start.countDown();
                int assigned = assign.get().statusCode(), removed = remove.get().statusCode();
                assertTrue(assigned == 200 || assigned == 422, "assign answered " + assigned);
                assertEquals(204, removed, "remove answered " + removed);
                var task = read(Account.OWNER, p, t);
                assertTrue(task.get("assigneeId").isNull(), "round " + round + ": the removed member still holds the task");
            }
        } finally {
            pool.shutdownNow();
        }
    }

    // --- fixtures -------------------------------------------------------------------------------

    long project() {
        long p = TestProjects.owned(projects, memberships, id(Account.OWNER), "Assignment fixture", TestProjects.uniqueKey("A")).getId();
        member(p, Account.SECOND, ProjectRole.MEMBER);
        member(p, Account.THIRD, ProjectRole.VIEWER);
        member(p, Account.DORMANT, ProjectRole.MEMBER);
        return p;
    }

    long task(long projectId) throws Exception {
        var created = expect(Account.OWNER, "POST", tasks(projectId), "{\"title\":\"Assignable\"}", 201);
        return json.readTree(created.body()).get("id").asLong();
    }

    JsonNode read(Account caller, long projectId, long taskId) throws Exception {
        return json.readTree(expect(caller, "GET", tasks(projectId) + "/" + taskId, null, 200).body());
    }

    void member(long projectId, Account account, ProjectRole role) {
        memberships.saveAndFlush(new ProjectMembership(projects.findById(projectId).orElseThrow(), id(account), role));
    }

    String body(Account account) {
        return "{\"accountId\":" + id(account) + "}";
    }

    static String tasks(long projectId) {
        return "/projects/" + projectId + "/tasks";
    }

    String members(long projectId, Account account) {
        return "/projects/" + projectId + "/members/" + id(account);
    }

    static String assignee(long projectId, long taskId) {
        return tasks(projectId) + "/" + taskId + "/assignee";
    }

    HttpResponse<String> expect(Account caller, String method, String path, String body, int status) throws Exception {
        var response = send(caller, method, path, body);
        assertEquals(status, response.statusCode(), caller + " " + method + " " + path + " " + body + ": " + response.body());
        if (status >= 400) {
            assertTrue(response.headers().firstValue("Content-Type").orElseThrow().startsWith("application/problem+json"));
            assertFalse(response.body().contains("Exception"));
        }
        return response;
    }

    HttpResponse<String> send(Account caller, String method, String path, String body) throws Exception {
        var builder = session(caller).request(path);
        if (body != null) builder.header("Content-Type", "application/json");
        return TestHttp.client().send(builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
            : HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }

    long id(Account account) {
        return session(account).accountId();
    }

    URI server() {
        return URI.create("http://127.0.0.1:" + environment.getProperty("local.server.port"));
    }

    TestHttp.Session session(Account account) {
        return SESSIONS.computeIfAbsent(server().toString(), ignored -> {
            var sessions = new EnumMap<Account, TestHttp.Session>(Account.class);
            for (Account each : Account.values()) sessions.put(each, TestHttp.newSession(server()));
            return sessions;
        }).get(account);
    }
}
