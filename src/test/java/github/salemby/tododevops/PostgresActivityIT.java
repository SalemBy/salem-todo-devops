package github.salemby.tododevops;

import github.salemby.tododevops.support.IsolatedPostgresSchema;
import github.salemby.tododevops.support.TestHttp;
import github.salemby.tododevops.support.TestProjects;
import github.salemby.tododevops.shared.security.CurrentAccount;
import github.salemby.tododevops.workmanagement.application.TaskService;
import github.salemby.tododevops.workmanagement.application.dto.request.UpdateTaskRequest;
import github.salemby.tododevops.workmanagement.domain.ProjectMembership;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
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

/** Issue #12: append-only activity history, written with each change and read newest first. */
@SpringBootTest(classes = SalemTodoDevOpsApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("postgres-test")
@ContextConfiguration(initializers = IsolatedPostgresSchema.class)
class PostgresActivityIT {
    /** OWNER owns each fixture project; SECOND and FOURTH are MEMBERs, THIRD a VIEWER, OUTSIDER nothing. */
    enum Account { OWNER, SECOND, THIRD, OUTSIDER, FOURTH }

    private static final Map<String, Map<Account, TestHttp.Session>> SESSIONS = new ConcurrentHashMap<>();

    @Autowired Environment environment;
    @Autowired ProjectRepository projects;
    @Autowired ProjectMembershipRepository memberships;
    @Autowired JdbcTemplate jdbc;
    @Autowired TaskService taskService;
    @Autowired PlatformTransactionManager transactionManager;
    private final ObjectMapper json = new ObjectMapper();

    // --- projects -------------------------------------------------------------------------------

    @Test
    void projectCreateUpdateArchiveAreRecorded() throws Exception {
        String key = TestProjects.uniqueKey("A");
        var created = json.readTree(expect(Account.OWNER, "POST", "/projects",
            "{\"name\":\"Alpha\",\"key\":\"" + key + "\",\"description\":\"Private notes\"}", 201).body());
        long p = created.get("id").asLong();

        var first = feed(Account.OWNER, p);
        assertEquals(1, first.size());
        var event = first.get(0);
        assertEvent(event, "PROJECT", p, "CREATED", Account.OWNER);
        assertTrue(event.get("taskId").isNull());
        assertEquals(p, event.get("projectId").asLong());
        assertEquals(List.of("name:null>Alpha", "key:null>" + key, "description:null>null"), changes(event));

        expect(Account.OWNER, "PUT", "/projects/" + p, "{\"name\":\"Beta\",\"description\":\"Private notes\"}", 200);
        expect(Account.OWNER, "DELETE", "/projects/" + p, null, 204);
        expect(Account.OWNER, "DELETE", "/projects/" + p, null, 204);

        var events = feed(Account.OWNER, p);
        assertEquals(List.of("ARCHIVED", "UPDATED", "CREATED"), actions(events), "newest first, one archive");
        assertEquals(List.of("name:Alpha>Beta"), changes(events.get(1)));
        assertEquals(List.of(), changes(events.get(0)));
    }

    @Test
    void unchangedProjectUpdateWritesNothing() throws Exception {
        long p = apiProject();
        expect(Account.OWNER, "PUT", "/projects/" + p, "{\"name\":\"Fixture\",\"description\":null}", 200);
        expect(Account.OWNER, "PUT", "/projects/" + p, "{\"name\":\"Fixture\",\"description\":\"Text\"}", 200);
        expect(Account.OWNER, "PUT", "/projects/" + p, "{\"name\":\"Fixture\",\"description\":\"Text\"}", 200);
        var events = feed(Account.OWNER, p);
        assertEquals(List.of("UPDATED", "CREATED"), actions(events));
        assertEquals(List.of("description:null>null"), changes(events.get(0)), "the text itself is never recorded");
    }

    @Test
    void feedPagesNewestFirstWithStableTieBreak() throws Exception {
        long p = project();
        for (int i = 0; i < 3; i++)
            jdbc.update("INSERT INTO activity_events(project_id,actor_account_id,resource_type,resource_id,action,occurred_at) "
                + "VALUES (?,?,'PROJECT',?,'UPDATED',TIMESTAMPTZ '2026-01-01 00:00:00+00')", p, id(Account.OWNER), p);
        var page0 = json.readTree(expect(Account.THIRD, "GET", activity(p) + "?size=2&page=0", null, 200).body());
        var page1 = json.readTree(expect(Account.THIRD, "GET", activity(p) + "?size=2&page=1", null, 200).body());
        var ids = new ArrayList<Long>();
        for (var page : List.of(page0, page1)) for (JsonNode item : page.get("items")) ids.add(item.get("id").asLong());
        assertEquals(3, ids.size());
        assertTrue(ids.get(0) > ids.get(1) && ids.get(1) > ids.get(2), "equal times fall back to id descending: " + ids);
        assertEquals(3, page0.get("totalElements").asInt());
        assertEquals(2, page0.get("totalPages").asInt());
        assertEquals(1, page1.get("page").asInt());
        assertEquals(2, page1.get("size").asInt());
    }

    @Test
    void viewerReadsOutsiderGets404OtherMethodsGet405() throws Exception {
        long p = project();
        expect(Account.THIRD, "GET", activity(p), null, 200);
        assertDetail(expect(Account.OUTSIDER, "GET", activity(p), null, 404), "Project not found");
        expect(Account.OWNER, "GET", "/projects/999999999/activity", null, 404);
        expect(Account.OWNER, "GET", "/projects/0/activity", null, 400);
        for (String method : List.of("POST", "PUT", "DELETE", "PATCH"))
            expect(Account.OWNER, method, activity(p), method.equals("DELETE") ? null : "{}", 405);
        expect(Account.OWNER, "GET", activity(p) + "?size=101", null, 400);
        expect(Account.OWNER, "GET", activity(p) + "?page=-1", null, 400);
        expect(Account.OWNER, "GET", activity(p) + "?size=0", null, 400);
    }

    @Test
    void occurredAtNeverChanges() throws Exception {
        long p = apiProject();
        var before = feed(Account.OWNER, p).get(0);
        expect(Account.OWNER, "DELETE", "/projects/" + p, null, 204);
        var after = feed(Account.OWNER, p);
        assertEquals(before, after.get(1), "the creation event is unchanged after archiving");
        assertFalse(after.get(0).get("occurredAt").stringValue().isBlank());
        assertEquals(after.get(0), feed(Account.THIRD, p).get(0), "archived projects keep their history readable");
    }

    // --- tasks ----------------------------------------------------------------------------------

    @Test
    void taskCreateRecordsInitialValues() throws Exception {
        long p = project(), a = label(p, "Alpha"), b = label(p, "Beta");
        long t = createTask(p, "{\"title\":\"T\",\"description\":\"Details\",\"priority\":\"HIGH\","
            + "\"dueDate\":\"2026-12-01\",\"labelIds\":[" + b + "," + a + "]}");
        var events = taskFeed(Account.OWNER, p, t);
        assertEquals(1, events.size());
        assertEvent(events.get(0), "TASK", t, "CREATED", Account.OWNER);
        assertEquals(t, events.get(0).get("taskId").asLong());
        assertEquals(List.of("title:null>T", "description:null>null", "status:null>PENDING", "type:null>TASK",
            "priority:null>HIGH", "dueDate:null>2026-12-01", "labelIds:null>" + a + "," + b), changes(events.get(0)));
    }

    @Test
    void boardMoveRecordsOnlyStatus() throws Exception {
        long p = project(), t = createTask(p, "{\"title\":\"Move\",\"description\":\"Keep\"}");
        expect(Account.SECOND, "PUT", task(p, t), "{\"title\":\"Move\",\"description\":\"Keep\",\"status\":\"IN_PROGRESS\"}", 200);
        var events = taskFeed(Account.OWNER, p, t);
        assertEquals(2, events.size());
        assertEvent(events.get(0), "TASK", t, "UPDATED", Account.SECOND);
        assertEquals(List.of("status:PENDING>IN_PROGRESS"), changes(events.get(0)));
    }

    @Test
    void identicalPutWritesNothing() throws Exception {
        long p = project(), a = label(p, "Alpha"), b = label(p, "Beta");
        long t = createTask(p, "{\"title\":\"Same\",\"labelIds\":[" + a + "," + b + "]}");
        expect(Account.OWNER, "PUT", task(p, t), "{\"title\":\"Same\",\"description\":null,\"status\":\"PENDING\",\"type\":\"TASK\","
            + "\"priority\":\"MEDIUM\",\"dueDate\":null,\"labelIds\":[" + a + "," + b + "]}", 200);
        expect(Account.OWNER, "PUT", task(p, t), "{\"title\":\"Same\",\"status\":\"PENDING\",\"labelIds\":[" + b + "," + a + "]}", 200);
        assertEquals(List.of("CREATED"), actions(taskFeed(Account.OWNER, p, t)));
    }

    @Test
    void dueDateOmittedVersusNull() throws Exception {
        long p = project(), t = createTask(p, "{\"title\":\"Due\",\"dueDate\":\"2026-12-01\"}");
        expect(Account.OWNER, "PUT", task(p, t), "{\"title\":\"Due\",\"status\":\"PENDING\"}", 200);
        assertEquals(1, taskFeed(Account.OWNER, p, t).size(), "an omitted due date is kept and not recorded");
        expect(Account.OWNER, "PUT", task(p, t), "{\"title\":\"Due\",\"status\":\"PENDING\",\"dueDate\":null}", 200);
        var events = taskFeed(Account.OWNER, p, t);
        assertEquals(List.of("dueDate:2026-12-01>null"), changes(events.get(0)));
    }

    @Test
    void labelsAssignAndArchiveAreRecorded() throws Exception {
        long p = project(), a = label(p, "Alpha"), b = label(p, "Beta");
        long t = createTask(p, "{\"title\":\"Labels\",\"labelIds\":[" + a + "]}");
        String second = String.valueOf(id(Account.SECOND));
        expect(Account.OWNER, "PUT", task(p, t) + "/labels/" + b, null, 204);
        expect(Account.OWNER, "PUT", task(p, t) + "/labels/" + b, null, 204);
        expect(Account.OWNER, "DELETE", task(p, t) + "/labels/" + b, null, 204);
        expect(Account.OWNER, "DELETE", task(p, t) + "/labels/" + b, null, 204);
        expect(Account.OWNER, "PUT", task(p, t) + "/assignee", "{\"accountId\":" + second + "}", 200);
        expect(Account.OWNER, "PUT", task(p, t) + "/assignee", "{\"accountId\":" + second + "}", 200);
        expect(Account.OWNER, "DELETE", task(p, t) + "/assignee", null, 204);
        expect(Account.OWNER, "DELETE", task(p, t) + "/assignee", null, 204);
        expect(Account.OWNER, "DELETE", task(p, t), null, 204);
        expect(Account.OWNER, "DELETE", task(p, t), null, 204);

        var events = taskFeed(Account.OWNER, p, t);
        assertEquals(List.of("ARCHIVED", "UPDATED", "UPDATED", "UPDATED", "UPDATED", "CREATED"), actions(events));
        assertEquals(List.of(), changes(events.get(0)));
        assertEquals(List.of("assigneeId:" + second + ">null"), changes(events.get(1)));
        assertEquals(List.of("assigneeId:null>" + second), changes(events.get(2)));
        assertEquals(List.of("labelIds:" + a + "," + b + ">" + a), changes(events.get(3)));
        assertEquals(List.of("labelIds:" + a + ">" + a + "," + b), changes(events.get(4)));
    }

    @Test
    void refusedWritesRecordNothing() throws Exception {
        long p = project(), t = createTask(p, "{\"title\":\"Refused\"}");
        expect(Account.OWNER, "PUT", task(p, t) + "/assignee", "{\"accountId\":" + id(Account.THIRD) + "}", 422);
        expect(Account.OWNER, "PUT", task(p, t), "{\"title\":\"Changed\",\"status\":\"PENDING\",\"labelIds\":[999999999]}", 404);
        expect(Account.THIRD, "PUT", task(p, t), "{\"title\":\"Changed\",\"status\":\"PENDING\"}", 403);
        expect(Account.OWNER, "DELETE", "/projects/" + p, null, 204);
        expect(Account.OWNER, "PUT", task(p, t), "{\"title\":\"Changed\",\"status\":\"PENDING\"}", 409);
        expect(Account.OWNER, "DELETE", task(p, t), null, 409);
        assertEquals(List.of("CREATED"), actions(taskFeed(Account.OWNER, p, t)));
        assertEquals("Refused", json.readTree(expect(Account.OWNER, "GET", task(p, t), null, 200).body()).get("title").stringValue());
    }

    @Test
    void rollbackAfterRecordingLeavesNoEvent() throws Exception {
        long p = project(), t = createTask(p, "{\"title\":\"Before\"}");
        String count = "SELECT count(*) FROM activity_events WHERE task_id = ?";
        var request = json.readValue("{\"title\":\"After\",\"status\":\"COMPLETED\"}", UpdateTaskRequest.class);
        var transactions = new TransactionTemplate(transactionManager);
        var failure = assertThrows(IllegalStateException.class, () -> transactions.executeWithoutResult(status -> {
            taskService.update(new CurrentAccount(id(Account.OWNER)), p, t, request);
            assertEquals(2, jdbc.queryForObject(count, Long.class, t), "the event is written inside the transaction");
            throw new IllegalStateException("roll back");
        }));
        assertEquals("roll back", failure.getMessage());
        assertEquals(1, jdbc.queryForObject(count, Long.class, t));
        assertEquals("Before", json.readTree(expect(Account.OWNER, "GET", task(p, t), null, 200).body()).get("title").stringValue());
    }

    @Test
    void descriptionTextNeverStored() throws Exception {
        long p = project(), t = createTask(p, "{\"title\":\"Secret\",\"description\":\"secret-marker-123\"}");
        expect(Account.OWNER, "PUT", task(p, t), "{\"title\":\"Secret\",\"description\":\"secret-marker-456\",\"status\":\"PENDING\"}", 200);
        expect(Account.OWNER, "PUT", "/projects/" + p, "{\"name\":\"Renamed\",\"description\":\"secret-marker-789\"}", 200);
        assertEquals(List.of("description:null>null"), changes(taskFeed(Account.OWNER, p, t).get(0)));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM activity_changes WHERE before_value LIKE '%secret-marker%' "
            + "OR after_value LIKE '%secret-marker%'", Long.class));
    }

    @Test
    void taskFeedShowsOnlyThatTask() throws Exception {
        long p = project(), t = createTask(p, "{\"title\":\"One\"}"), u = createTask(p, "{\"title\":\"Two\"}");
        expect(Account.OWNER, "PUT", task(p, t), "{\"title\":\"One\",\"status\":\"COMPLETED\"}", 200);
        var events = taskFeed(Account.THIRD, p, t);
        assertEquals(2, events.size());
        for (JsonNode event : events) assertEquals(t, event.get("taskId").asLong());
        assertEquals(1, taskFeed(Account.THIRD, p, u).size());

        expect(Account.OWNER, "DELETE", task(p, u), null, 204);
        assertEquals(List.of("ARCHIVED", "CREATED"), actions(taskFeed(Account.THIRD, p, u)), "archived tasks keep their history");

        long q = project(), v = createTask(q, "{\"title\":\"Elsewhere\"}");
        assertDetail(expect(Account.OWNER, "GET", task(p, v) + "/activity", null, 404), "Task not found");
        assertDetail(expect(Account.OWNER, "GET", task(p, 999999999) + "/activity", null, 404), "Task not found");
        assertDetail(expect(Account.OUTSIDER, "GET", task(p, t) + "/activity", null, 404), "Project not found");
        expect(Account.OWNER, "GET", task(p, t) + "/activity?size=101", null, 400);
        expect(Account.OWNER, "PUT", task(p, t) + "/activity", "{}", 405);
    }

    // --- fixtures -------------------------------------------------------------------------------

    long createTask(long projectId, String body) throws Exception {
        return json.readTree(expect(Account.OWNER, "POST", "/projects/" + projectId + "/tasks", body, 201).body()).get("id").asLong();
    }

    long label(long projectId, String name) throws Exception {
        return json.readTree(expect(Account.OWNER, "POST", "/projects/" + projectId + "/labels",
            "{\"name\":\"" + name + "\"}", 201).body()).get("id").asLong();
    }

    static String task(long projectId, long taskId) {
        return "/projects/" + projectId + "/tasks/" + taskId;
    }

    /** Every event of the task feed, newest first. */
    List<JsonNode> taskFeed(Account who, long projectId, long taskId) throws Exception {
        return items(expect(who, "GET", task(projectId, taskId) + "/activity?size=100", null, 200));
    }

    /** A project saved directly (no events), with the standard members. */
    long project() {
        long p = TestProjects.owned(projects, memberships, id(Account.OWNER), "Activity fixture", TestProjects.uniqueKey("H")).getId();
        addMembers(p);
        return p;
    }

    /** A project created through the API (one CREATED event), with the standard members added directly. */
    long apiProject() throws Exception {
        long p = json.readTree(expect(Account.OWNER, "POST", "/projects",
            "{\"name\":\"Fixture\",\"key\":\"" + TestProjects.uniqueKey("F") + "\"}", 201).body()).get("id").asLong();
        addMembers(p);
        return p;
    }

    void addMembers(long p) {
        member(p, Account.SECOND, ProjectRole.MEMBER);
        member(p, Account.THIRD, ProjectRole.VIEWER);
        member(p, Account.FOURTH, ProjectRole.MEMBER);
    }

    void member(long projectId, Account account, ProjectRole role) {
        memberships.saveAndFlush(new ProjectMembership(projects.findById(projectId).orElseThrow(), id(account), role));
    }

    static String activity(long projectId) {
        return "/projects/" + projectId + "/activity";
    }

    /** Every event of the project feed, newest first. */
    List<JsonNode> feed(Account who, long projectId) throws Exception {
        return items(expect(who, "GET", activity(projectId) + "?size=100", null, 200));
    }

    List<JsonNode> items(HttpResponse<String> response) throws Exception {
        var items = new ArrayList<JsonNode>();
        for (JsonNode item : json.readTree(response.body()).get("items")) items.add(item);
        return items;
    }

    static List<String> actions(List<JsonNode> events) {
        return events.stream().map(e -> e.get("action").stringValue()).toList();
    }

    /** Each change as "field:before>after", with null for no value. */
    static List<String> changes(JsonNode event) {
        var changes = new ArrayList<String>();
        for (JsonNode c : event.get("changes"))
            changes.add(c.get("field").stringValue() + ":" + text(c.get("before")) + ">" + text(c.get("after")));
        return changes;
    }

    static String text(JsonNode value) {
        return value == null || value.isNull() ? "null" : value.stringValue();
    }

    void assertEvent(JsonNode event, String resourceType, long resourceId, String action, Account actor) {
        assertEquals(resourceType, event.get("resourceType").stringValue(), event.toString());
        assertEquals(resourceId, event.get("resourceId").asLong(), event.toString());
        assertEquals(action, event.get("action").stringValue(), event.toString());
        assertEquals(id(actor), event.get("actorId").asLong(), event.toString());
    }

    void assertDetail(HttpResponse<String> response, String detail) throws Exception {
        assertEquals(detail, json.readTree(response.body()).get("detail").stringValue());
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
