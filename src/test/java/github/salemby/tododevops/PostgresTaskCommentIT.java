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

import static org.junit.jupiter.api.Assertions.*;

/** Issue #11: plain-text task comments with a fixed author, edited by the author and soft-deleted. */
@SpringBootTest(classes = SalemTodoDevOpsApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("postgres-test")
@ContextConfiguration(initializers = IsolatedPostgresSchema.class)
class PostgresTaskCommentIT {
    static final String SIZE = "Comment body must contain at most 10000 characters";
    static final String NOT_AUTHOR = "Only the author can edit this comment";
    static final String NOT_AUTHOR_OR_OWNER = "Only the author or a project owner can delete this comment";
    static final String ROLE_FORBIDDEN = "Your project role does not allow this action";

    /** OWNER owns each fixture project; SECOND and FOURTH are MEMBERs, THIRD a VIEWER, OUTSIDER nothing. */
    enum Account { OWNER, SECOND, THIRD, OUTSIDER, FOURTH }

    private static final Map<String, Map<Account, TestHttp.Session>> SESSIONS = new ConcurrentHashMap<>();

    @Autowired Environment environment;
    @Autowired ProjectRepository projects;
    @Autowired ProjectMembershipRepository memberships;
    @Autowired JdbcTemplate jdbc;
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void membersPostListReadAndEdit() throws Exception {
        long p = project(), t = task(p);
        var first = post(Account.OWNER, p, t, "First");
        var created = expect(Account.SECOND, "POST", comments(p, t), body("Second"), 201);
        var second = json.readTree(created.body());
        assertTrue(created.headers().firstValue("Location").orElseThrow().endsWith(comments(p, t) + "/" + second.get("id").asLong()));
        assertEquals(id(Account.SECOND), second.get("authorId").asLong());
        assertEquals(t, second.get("taskId").asLong());
        assertEquals("Second", second.get("body").stringValue());

        var page = json.readTree(expect(Account.THIRD, "GET", comments(p, t), null, 200).body());
        assertEquals(List.of("First", "Second"), bodies(page));
        assertEquals(id(Account.OWNER), page.get("items").get(0).get("authorId").asLong());
        var paged = json.readTree(expect(Account.THIRD, "GET", comments(p, t) + "?size=1&page=1", null, 200).body());
        assertEquals(List.of("Second"), bodies(paged));
        assertEquals(2, paged.get("totalElements").asInt());
        assertEquals(2, paged.get("totalPages").asInt());
        expect(Account.THIRD, "GET", comments(p, t) + "?size=101", null, 400);
        expect(Account.THIRD, "GET", comments(p, t) + "?page=-1", null, 400);

        String path = comments(p, t) + "/" + second.get("id").asLong();
        var before = json.readTree(expect(Account.THIRD, "GET", path, null, 200).body());
        var edited = json.readTree(expect(Account.SECOND, "PUT", path, body("Second, edited"), 200).body());
        var after = json.readTree(expect(Account.THIRD, "GET", path, null, 200).body());
        assertEquals("Second, edited", edited.get("body").stringValue());
        assertEquals("Second, edited", after.get("body").stringValue());
        assertEquals(before.get("createdAt"), after.get("createdAt"), "creation time never changes");
        assertEquals(second.get("createdAt"), before.get("createdAt"), "the 201 response reports the stored creation time");
        assertEquals(edited.get("updatedAt"), after.get("updatedAt"), "the edit response reports the stored update time");
        assertEquals(id(Account.SECOND), after.get("authorId").asLong(), "authorship never changes");
        assertTrue(after.get("updatedAt").stringValue().compareTo(after.get("createdAt").stringValue()) > 0);
        assertEquals("First", first.get("body").stringValue());
    }

    @Test
    void bodyValidation() throws Exception {
        long p = project(), t = task(p);
        for (String invalid : List.of(body(""), body("   "), body("\\u200B"), body("a\\u0000b"), "{}", "{\"body\":null}",
                "{\"body\":5}", "not json"))
            expect(Account.OWNER, "POST", comments(p, t), invalid, 400);
        post(Account.OWNER, p, t, "a".repeat(10000));
        var tooLong = expect(Account.OWNER, "POST", comments(p, t), body("a".repeat(10001)), 400);
        assertTrue(tooLong.body().contains(SIZE), tooLong.body());
        var page = json.readTree(expect(Account.OWNER, "GET", comments(p, t), null, 200).body());
        assertEquals(1, page.get("totalElements").asInt(), "only the valid comment was stored");
    }

    @Test
    void markupIsPlainText() throws Exception {
        long p = project(), t = task(p);
        String markup = "<script>alert(1)</script> **bold** & \"quotes\"";
        var created = expect(Account.OWNER, "POST", comments(p, t), "{\"body\":" + json.writeValueAsString(markup) + "}", 201);
        assertTrue(created.headers().firstValue("Content-Type").orElseThrow().startsWith("application/json"));
        long c = json.readTree(created.body()).get("id").asLong();
        assertEquals(markup, json.readTree(created.body()).get("body").stringValue());
        assertEquals(markup, json.readTree(expect(Account.THIRD, "GET", comments(p, t) + "/" + c, null, 200).body()).get("body").stringValue());
    }

    @Test
    void viewersReadButCannotPost() throws Exception {
        long p = project(), t = task(p);
        post(Account.OWNER, p, t, "Visible");
        expect(Account.THIRD, "POST", comments(p, t), body("Viewer"), 403);
        assertEquals(1, json.readTree(expect(Account.THIRD, "GET", comments(p, t), null, 200).body()).get("totalElements").asInt());
        expect(Account.OUTSIDER, "POST", comments(p, t), body("Outsider"), 404);
        expect(Account.OUTSIDER, "GET", comments(p, t), null, 404);
    }

    @Test
    void onlyTheAuthorEdits() throws Exception {
        long p = project(), t = task(p);
        long c = post(Account.SECOND, p, t, "Mine").get("id").asLong();
        var byOwner = expect(Account.OWNER, "PUT", comments(p, t) + "/" + c, body("Rewritten"), 403);
        assertEquals(NOT_AUTHOR, json.readTree(byOwner.body()).get("detail").stringValue());
        expect(Account.FOURTH, "PUT", comments(p, t) + "/" + c, body("Rewritten"), 403);
        expect(Account.THIRD, "PUT", comments(p, t) + "/" + c, body("Rewritten"), 403);
        assertEquals("Mine", json.readTree(expect(Account.OWNER, "GET", comments(p, t) + "/" + c, null, 200).body()).get("body").stringValue());
    }

    @Test
    void idsMustMatchTheirParents() throws Exception {
        long p = project(), t = task(p), sibling = task(p);
        long c = post(Account.OWNER, p, t, "On the first task").get("id").asLong();
        assertDetail(expect(Account.OWNER, "GET", comments(p, sibling) + "/" + c, null, 404), "Comment not found");
        assertDetail(expect(Account.OWNER, "PUT", comments(p, sibling) + "/" + c, body("x"), 404), "Comment not found");
        long other = project(), elsewhere = task(other);
        assertDetail(expect(Account.OWNER, "GET", comments(p, elsewhere), null, 404), "Task not found");
        assertDetail(expect(Account.OWNER, "POST", comments(p, elsewhere), body("x"), 404), "Task not found");
        expect(Account.OWNER, "GET", comments(p, t) + "/999999999", null, 404);
        expect(Account.OWNER, "GET", comments(p, 999999999), null, 404);
        expect(Account.OWNER, "GET", comments(0, t), null, 400);
    }

    @Test
    void lifecycle() throws Exception {
        long p = project(), t = task(p);
        long c = post(Account.OWNER, p, t, "Before archiving").get("id").asLong();
        expect(Account.OWNER, "DELETE", "/projects/" + p + "/tasks/" + t, null, 204);
        assertDetail(expect(Account.OWNER, "POST", comments(p, t), body("Late"), 404), "Task not found");
        assertDetail(expect(Account.OWNER, "PUT", comments(p, t) + "/" + c, body("Late"), 404), "Task not found");
        expect(Account.THIRD, "GET", comments(p, t), null, 200);
        expect(Account.THIRD, "GET", comments(p, t) + "/" + c, null, 200);

        long q = project(), u = task(q);
        long d = post(Account.OWNER, q, u, "Before archiving").get("id").asLong();
        expect(Account.OWNER, "DELETE", "/projects/" + q, null, 204);
        expect(Account.OWNER, "POST", comments(q, u), body("Late"), 409);
        expect(Account.OWNER, "PUT", comments(q, u) + "/" + d, body("Late"), 409);
        expect(Account.THIRD, "GET", comments(q, u), null, 200);
        expect(Account.THIRD, "GET", comments(q, u) + "/" + d, null, 200);
    }

    @Test
    void authorDeletesAndTheRowIsKept() throws Exception {
        long p = project(), t = task(p);
        long c = post(Account.SECOND, p, t, "Soon gone").get("id").asLong();
        post(Account.OWNER, p, t, "Stays");
        expect(Account.SECOND, "DELETE", comments(p, t) + "/" + c, null, 204);
        assertEquals(List.of("Stays"), bodies(json.readTree(expect(Account.THIRD, "GET", comments(p, t), null, 200).body())));
        assertDetail(expect(Account.THIRD, "GET", comments(p, t) + "/" + c, null, 404), "Comment not found");
        assertDetail(expect(Account.SECOND, "DELETE", comments(p, t) + "/" + c, null, 404), "Comment not found");
        assertDetail(expect(Account.SECOND, "PUT", comments(p, t) + "/" + c, body("Back"), 404), "Comment not found");
        var row = jdbc.queryForMap("select active, body, author_account_id from task_comments where id = ?", c);
        assertEquals(false, row.get("active"));
        assertEquals("Soon gone", row.get("body"));
        assertEquals(id(Account.SECOND), ((Number) row.get("author_account_id")).longValue());
    }

    @Test
    void ownerModeratesButOthersCannot() throws Exception {
        long p = project(), t = task(p);
        long first = post(Account.SECOND, p, t, "First").get("id").asLong();
        long second = post(Account.SECOND, p, t, "Second").get("id").asLong();
        var byMember = expect(Account.FOURTH, "DELETE", comments(p, t) + "/" + first, null, 403);
        assertDetail(byMember, NOT_AUTHOR_OR_OWNER);
        assertDetail(expect(Account.THIRD, "DELETE", comments(p, t) + "/" + first, null, 403), ROLE_FORBIDDEN);
        // A role that can never delete gets 403 before any state check, like POST and PUT.
        assertDetail(expect(Account.THIRD, "DELETE", comments(p, t) + "/999999999", null, 403), ROLE_FORBIDDEN);
        assertDetail(expect(Account.THIRD, "DELETE", comments(p, 999999999) + "/" + first, null, 403), ROLE_FORBIDDEN);
        expect(Account.OUTSIDER, "DELETE", comments(p, t) + "/" + first, null, 404);
        expect(Account.OWNER, "DELETE", comments(p, t) + "/" + second, null, 204);
        assertEquals(List.of("First"), bodies(json.readTree(expect(Account.OWNER, "GET", comments(p, t), null, 200).body())));
    }

    @Test
    void demotedAuthorCanNoLongerEditOrDelete() throws Exception {
        long p = project(), t = task(p);
        long c = post(Account.SECOND, p, t, "Said as a member").get("id").asLong();
        expect(Account.OWNER, "PUT", "/projects/" + p + "/members/" + id(Account.SECOND), "{\"role\":\"VIEWER\"}", 200);
        expect(Account.SECOND, "PUT", comments(p, t) + "/" + c, body("Changed"), 403);
        expect(Account.SECOND, "DELETE", comments(p, t) + "/" + c, null, 403);
        assertEquals("Said as a member",
            json.readTree(expect(Account.OWNER, "GET", comments(p, t) + "/" + c, null, 200).body()).get("body").stringValue());
    }

    @Test
    void commentsSurviveTheAuthorLeaving() throws Exception {
        long p = project(), t = task(p);
        post(Account.SECOND, p, t, "Goodbye");
        expect(Account.SECOND, "DELETE", "/projects/" + p + "/members/" + id(Account.SECOND), null, 204);
        var page = json.readTree(expect(Account.OWNER, "GET", comments(p, t), null, 200).body());
        assertEquals(List.of("Goodbye"), bodies(page));
        assertEquals(id(Account.SECOND), page.get("items").get(0).get("authorId").asLong());
    }

    @Test
    void deleteFollowsLifecycle() throws Exception {
        long p = project(), t = task(p);
        long c = post(Account.OWNER, p, t, "Kept").get("id").asLong();
        expect(Account.OWNER, "DELETE", "/projects/" + p + "/tasks/" + t, null, 204);
        assertDetail(expect(Account.OWNER, "DELETE", comments(p, t) + "/" + c, null, 404), "Task not found");

        long q = project(), u = task(q);
        long d = post(Account.OWNER, q, u, "Kept").get("id").asLong();
        expect(Account.OWNER, "DELETE", "/projects/" + q, null, 204);
        expect(Account.OWNER, "DELETE", comments(q, u) + "/" + d, null, 409);
        assertDetail(expect(Account.THIRD, "DELETE", comments(q, u) + "/" + d, null, 403), ROLE_FORBIDDEN);
        assertEquals(1, json.readTree(expect(Account.OWNER, "GET", comments(q, u), null, 200).body()).get("totalElements").asInt());
    }

    // --- fixtures -------------------------------------------------------------------------------

    long project() {
        long p = TestProjects.owned(projects, memberships, id(Account.OWNER), "Comment fixture", TestProjects.uniqueKey("C")).getId();
        member(p, Account.SECOND, ProjectRole.MEMBER);
        member(p, Account.THIRD, ProjectRole.VIEWER);
        member(p, Account.FOURTH, ProjectRole.MEMBER);
        return p;
    }

    long task(long projectId) throws Exception {
        return json.readTree(expect(Account.OWNER, "POST", "/projects/" + projectId + "/tasks", "{\"title\":\"Discuss\"}", 201).body())
            .get("id").asLong();
    }

    JsonNode post(Account caller, long projectId, long taskId, String text) throws Exception {
        return json.readTree(expect(caller, "POST", comments(projectId, taskId), body(text), 201).body());
    }

    void member(long projectId, Account account, ProjectRole role) {
        memberships.saveAndFlush(new ProjectMembership(projects.findById(projectId).orElseThrow(), id(account), role));
    }

    /** JSON with the text inserted as written: callers pass JSON escapes such as \\u0000 on purpose. */
    static String body(String text) {
        return "{\"body\":\"" + text + "\"}";
    }

    static String comments(long projectId, long taskId) {
        return "/projects/" + projectId + "/tasks/" + taskId + "/comments";
    }

    static List<String> bodies(JsonNode page) {
        var bodies = new ArrayList<String>();
        for (JsonNode item : page.get("items")) bodies.add(item.get("body").stringValue());
        return bodies;
    }

    void assertDetail(HttpResponse<String> response, String detail) throws Exception {
        assertEquals(detail, json.readTree(response.body()).get("detail").stringValue());
    }

    HttpResponse<String> expect(Account caller, String method, String path, String body, int status) throws Exception {
        var response = send(caller, method, path, body);
        String shown = body == null || body.length() < 200 ? body : body.substring(0, 200) + "...";
        assertEquals(status, response.statusCode(), caller + " " + method + " " + path + " " + shown + ": " + response.body());
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
