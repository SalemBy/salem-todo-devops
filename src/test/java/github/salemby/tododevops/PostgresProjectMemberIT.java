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
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/** Issue #9: membership endpoints, the last-owner rule, and leaving a project. */
@SpringBootTest(classes = SalemTodoDevOpsApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("postgres-test")
@ContextConfiguration(initializers = IsolatedPostgresSchema.class)
class PostgresProjectMemberIT {
    /** OWNER owns each fixture project; the others start with no membership. */
    enum Account { OWNER, SECOND, THIRD, OUTSIDER }

    private static final Map<String, Map<Account, TestHttp.Session>> SESSIONS = new ConcurrentHashMap<>();

    @Autowired Environment environment;
    @Autowired ProjectRepository projects;
    @Autowired ProjectMembershipRepository memberships;
    @Autowired JdbcTemplate jdbc;
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void addsListsAndReadsMembersInRoleOrder() throws Exception {
        long p = project();
        expect(Account.OWNER, "POST", members(p), add(Account.THIRD, "VIEWER"), 201);
        var added = send(Account.OWNER, "POST", members(p), add(Account.SECOND, "MEMBER"));
        assertEquals(201, added.statusCode(), added.body());
        assertTrue(added.headers().firstValue("Location").orElseThrow()
            .endsWith(members(p) + "/" + id(Account.SECOND)));
        var member = json.readTree(added.body());
        assertEquals(id(Account.SECOND), member.get("accountId").asLong());
        assertEquals(session(Account.SECOND).email(), member.get("email").stringValue());
        assertEquals("HTTP test", member.get("displayName").stringValue());
        assertEquals("MEMBER", member.get("role").stringValue());
        assertNotNull(member.get("joinedAt").stringValue());

        var page = json.readTree(send(Account.THIRD, "GET", members(p), null).body());
        assertEquals(3, page.get("totalElements").asInt());
        assertEquals(List.of("OWNER", "MEMBER", "VIEWER"), roles(page));
        assertEquals(id(Account.OWNER), page.get("items").get(0).get("accountId").asLong());

        var second = json.readTree(send(Account.OWNER, "GET", members(p) + "?page=1&size=1", null).body());
        assertEquals(List.of("MEMBER"), roles(second));
        assertEquals(1, second.get("page").asInt());
        assertEquals(3, second.get("totalPages").asInt());
        expect(Account.OWNER, "GET", members(p) + "?page=-1", null, 400);
        expect(Account.OWNER, "GET", members(p) + "?size=101", null, 400);

        var read = json.readTree(send(Account.THIRD, "GET", members(p) + "/" + id(Account.SECOND), null).body());
        assertEquals("MEMBER", read.get("role").stringValue());
    }

    @Test
    void addingNormalizesEmailAndRejectsInvalidRequests() throws Exception {
        long p = project();
        String spaced = "  " + session(Account.SECOND).email().toUpperCase() + " ";
        expect(Account.OWNER, "POST", members(p), "{\"email\":\"" + spaced + "\",\"role\":\"VIEWER\"}", 201);
        expect(Account.OWNER, "POST", members(p), add(Account.SECOND, "OWNER"), 409);
        assertEquals(ProjectRole.VIEWER, role(p, Account.SECOND), "A duplicate add must not change the role");

        expect(Account.OWNER, "POST", members(p), "{\"email\":\"nobody-" + p + "@example.com\",\"role\":\"MEMBER\"}", 422);
        expect(Account.OWNER, "POST", members(p), "{\"email\":\"not-an-email\",\"role\":\"MEMBER\"}", 400);
        expect(Account.OWNER, "POST", members(p), "{\"email\":\"\",\"role\":\"MEMBER\"}", 400);
        expect(Account.OWNER, "POST", members(p), "{\"email\":\"" + session(Account.THIRD).email() + "\",\"role\":\"ADMIN\"}", 400);
        expect(Account.OWNER, "POST", members(p), "{\"email\":\"" + session(Account.THIRD).email() + "\"}", 400);
        expect(Account.OWNER, "PUT", members(p) + "/" + id(Account.SECOND), "{\"role\":\"owner\"}", 400);
        expect(Account.OWNER, "GET", members(p) + "/0", null, 400);
        assertNull(role(p, Account.THIRD), "Rejected requests must not add members");
    }

    @Test
    void onlyOwnersManageMembers() throws Exception {
        long p = project();
        member(p, Account.SECOND, ProjectRole.MEMBER);
        member(p, Account.THIRD, ProjectRole.VIEWER);
        String outsider = add(Account.OUTSIDER, "VIEWER");
        for (Account caller : List.of(Account.SECOND, Account.THIRD)) {
            expect(caller, "GET", members(p), null, 200);
            expect(caller, "GET", members(p) + "/" + id(Account.OWNER), null, 200);
            expect(caller, "POST", members(p), outsider, 403);
            expect(caller, "PUT", members(p) + "/" + id(Account.OWNER), "{\"role\":\"VIEWER\"}", 403);
            expect(caller, "DELETE", members(p) + "/" + id(Account.OWNER), null, 403);
        }
        for (String[] request : new String[][]{{"GET", members(p), null}, {"GET", members(p) + "/" + id(Account.OWNER), null},
                {"POST", members(p), outsider}, {"PUT", members(p) + "/" + id(Account.SECOND), "{\"role\":\"OWNER\"}"},
                {"DELETE", members(p) + "/" + id(Account.SECOND), null}}) {
            expect(Account.OUTSIDER, request[0], request[1], request[2], 404);
        }
        assertEquals(ProjectRole.MEMBER, role(p, Account.SECOND));
        assertEquals(ProjectRole.VIEWER, role(p, Account.THIRD));
        assertNull(role(p, Account.OUTSIDER));
    }

    @Test
    void membershipChangesGrantAndRevokeAccessImmediately() throws Exception {
        long p = project();
        expect(Account.THIRD, "GET", "/projects/" + p, null, 404);
        expect(Account.OWNER, "POST", members(p), add(Account.THIRD, "VIEWER"), 201);
        expect(Account.THIRD, "GET", "/projects/" + p, null, 200);
        expect(Account.THIRD, "POST", "/projects/" + p + "/tasks", "{\"title\":\"Viewer task\"}", 403);

        var promoted = send(Account.OWNER, "PUT", members(p) + "/" + id(Account.THIRD), "{\"role\":\"MEMBER\"}");
        assertEquals(200, promoted.statusCode(), promoted.body());
        assertEquals("MEMBER", json.readTree(promoted.body()).get("role").stringValue());
        expect(Account.THIRD, "POST", "/projects/" + p + "/tasks", "{\"title\":\"Member task\"}", 201);

        expect(Account.OWNER, "DELETE", members(p) + "/" + id(Account.THIRD), null, 204);
        expect(Account.THIRD, "GET", "/projects/" + p, null, 404);
        expect(Account.OWNER, "GET", members(p) + "/" + id(Account.THIRD), null, 404);
    }

    @Test
    void settingTheSameRoleChangesNothing() throws Exception {
        long p = project();
        member(p, Account.SECOND, ProjectRole.MEMBER);
        jdbc.update("UPDATE project_memberships SET updated_at = TIMESTAMPTZ '2000-01-01 00:00:00Z' WHERE project_id = ?", p);
        var response = send(Account.OWNER, "PUT", members(p) + "/" + id(Account.SECOND), "{\"role\":\"MEMBER\"}");
        assertEquals(200, response.statusCode(), response.body());
        assertEquals("2000-01-01T00:00:00Z", json.readTree(response.body()).get("updatedAt").stringValue());
    }

    @Test
    void membersAndViewersCanLeave() throws Exception {
        long p = project();
        member(p, Account.SECOND, ProjectRole.MEMBER);
        member(p, Account.THIRD, ProjectRole.VIEWER);
        expect(Account.SECOND, "DELETE", members(p) + "/" + id(Account.SECOND), null, 204);
        expect(Account.THIRD, "DELETE", members(p) + "/" + id(Account.THIRD), null, 204);
        expect(Account.SECOND, "GET", "/projects/" + p, null, 404);
        expect(Account.THIRD, "GET", "/projects/" + p, null, 404);
        expect(Account.SECOND, "DELETE", members(p) + "/" + id(Account.SECOND), null, 404);
    }

    @Test
    void projectsAlwaysKeepAnOwner() throws Exception {
        long p = project();
        String owner = members(p) + "/" + id(Account.OWNER);
        expect(Account.OWNER, "PUT", owner, "{\"role\":\"MEMBER\"}", 409);
        expect(Account.OWNER, "DELETE", owner, null, 409);
        assertEquals(ProjectRole.OWNER, role(p, Account.OWNER));

        expect(Account.OWNER, "POST", members(p), add(Account.SECOND, "OWNER"), 201);
        expect(Account.OWNER, "PUT", owner, "{\"role\":\"VIEWER\"}", 200);
        expect(Account.SECOND, "DELETE", owner, null, 204);
        String second = members(p) + "/" + id(Account.SECOND);
        expect(Account.SECOND, "PUT", second, "{\"role\":\"MEMBER\"}", 409);
        expect(Account.SECOND, "DELETE", second, null, 409);
        assertEquals(ProjectRole.OWNER, role(p, Account.SECOND));
    }

    @Test
    void disabledOwnersDoNotCountAsOwners() throws Exception {
        long p = project();
        var disabled = TestHttp.newSession(server());
        expect(Account.OWNER, "POST", members(p), "{\"email\":\"" + disabled.email() + "\",\"role\":\"OWNER\"}", 201);
        jdbc.update("UPDATE accounts SET active = FALSE WHERE id = ?", disabled.accountId());

        expect(Account.OWNER, "DELETE", members(p) + "/" + id(Account.OWNER), null, 409);
        var listed = json.readTree(send(Account.OWNER, "GET", members(p), null).body());
        assertEquals(2, listed.get("totalElements").asInt(), "Disabled members stay listed so owners can remove them");
        expect(Account.OWNER, "DELETE", members(p) + "/" + disabled.accountId(), null, 204);
        expect(Account.OWNER, "POST", members(p), "{\"email\":\"" + disabled.email() + "\",\"role\":\"MEMBER\"}", 422);
    }

    @Test
    void archivedProjectsKeepMembershipManagement() throws Exception {
        long p = project();
        expect(Account.OWNER, "DELETE", "/projects/" + p, null, 204);
        expect(Account.OWNER, "POST", members(p), add(Account.SECOND, "VIEWER"), 201);
        expect(Account.SECOND, "GET", "/projects/" + p, null, 200);
        expect(Account.OWNER, "PUT", members(p) + "/" + id(Account.SECOND), "{\"role\":\"MEMBER\"}", 200);
        expect(Account.SECOND, "POST", "/projects/" + p + "/tasks", "{\"title\":\"Late\"}", 409);
        expect(Account.SECOND, "DELETE", members(p) + "/" + id(Account.SECOND), null, 204);
    }

    @Test
    void concurrentOwnerRemovalsNeverLeaveAProjectWithoutAnOwner() throws Exception {
        var pool = Executors.newFixedThreadPool(2);
        try {
            for (int round = 0; round < 5; round++) {
                long p = project();
                member(p, Account.SECOND, ProjectRole.OWNER);
                var start = new CountDownLatch(1);
                var first = pool.submit(() -> { start.await(); return send(Account.OWNER, "DELETE", members(p) + "/" + id(Account.SECOND), null); });
                var second = pool.submit(() -> { start.await(); return send(Account.SECOND, "DELETE", members(p) + "/" + id(Account.OWNER), null); });
                start.countDown();
                var statuses = new ArrayList<>(List.of(first.get(20, TimeUnit.SECONDS).statusCode(),
                    second.get(20, TimeUnit.SECONDS).statusCode()));
                statuses.sort(null);
                // Writes serialize on the project lock. The winner removes the other owner, whose own
                // request then runs as a non-member and receives 404.
                assertEquals(List.of(204, 404), statuses, "Exactly one removal may win in round " + round);
                assertEquals(1, memberships.findAllByProjectIdAndRole(p, ProjectRole.OWNER).size());
            }
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void concurrentOwnersLeavingKeepTheLastOwner() throws Exception {
        var pool = Executors.newFixedThreadPool(2);
        try {
            for (int round = 0; round < 5; round++) {
                long p = project();
                member(p, Account.SECOND, ProjectRole.OWNER);
                var start = new CountDownLatch(1);
                var first = pool.submit(() -> { start.await(); return send(Account.OWNER, "DELETE", members(p) + "/" + id(Account.OWNER), null); });
                var second = pool.submit(() -> { start.await(); return send(Account.SECOND, "DELETE", members(p) + "/" + id(Account.SECOND), null); });
                start.countDown();
                var statuses = new ArrayList<>(List.of(first.get(20, TimeUnit.SECONDS).statusCode(),
                    second.get(20, TimeUnit.SECONDS).statusCode()));
                statuses.sort(null);
                assertEquals(List.of(204, 409), statuses, "Only one owner may leave in round " + round);
                assertEquals(1, memberships.findAllByProjectIdAndRole(p, ProjectRole.OWNER).size());
            }
        } finally {
            pool.shutdownNow();
        }
    }

    private long project() {
        return TestProjects.owned(projects, memberships, id(Account.OWNER), "Member fixture", TestProjects.uniqueKey("M")).getId();
    }

    private void member(long projectId, Account account, ProjectRole role) {
        memberships.saveAndFlush(new ProjectMembership(projects.findById(projectId).orElseThrow(), id(account), role));
    }

    private ProjectRole role(long projectId, Account account) {
        return memberships.findByProjectIdAndAccountId(projectId, id(account)).map(ProjectMembership::getRole).orElse(null);
    }

    private String add(Account account, String role) {
        return "{\"email\":\"" + session(account).email() + "\",\"role\":\"" + role + "\"}";
    }

    private static String members(long projectId) {
        return "/projects/" + projectId + "/members";
    }

    private List<String> roles(JsonNode page) {
        var roles = new ArrayList<String>();
        for (JsonNode item : page.get("items")) roles.add(item.get("role").stringValue());
        return roles;
    }

    private void expect(Account caller, String method, String path, String body, int status) throws Exception {
        var response = send(caller, method, path, body);
        assertEquals(status, response.statusCode(), caller + " " + method + " " + path + ": " + response.body());
        if (status >= 400) {
            assertTrue(response.headers().firstValue("Content-Type").orElseThrow().startsWith("application/problem+json"));
            assertFalse(response.body().contains("Exception"));
        }
    }

    private HttpResponse<String> send(Account caller, String method, String path, String body) throws Exception {
        var builder = session(caller).request(path);
        if (body != null) builder.header("Content-Type", "application/json");
        return TestHttp.client().send(builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
            : HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }

    private long id(Account account) {
        return session(account).accountId();
    }

    private URI server() {
        return URI.create("http://127.0.0.1:" + environment.getProperty("local.server.port"));
    }

    private TestHttp.Session session(Account account) {
        return SESSIONS.computeIfAbsent(server().toString(), ignored -> {
            var sessions = new EnumMap<Account, TestHttp.Session>(Account.class);
            for (Account each : Account.values()) sessions.put(each, TestHttp.newSession(server()));
            return sessions;
        }).get(account);
    }
}
