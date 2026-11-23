package github.salemby.tododevops;

import github.salemby.tododevops.support.AuthClient;
import github.salemby.tododevops.support.SharedSchemaApps;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import javax.sql.DataSource;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static github.salemby.tododevops.support.AuthClient.PASSWORD;
import static org.junit.jupiter.api.Assertions.*;

/** Issue #67: configured system administrators deactivate and reactivate accounts and recover ownerless projects. */
class PostgresAdministrationIT {
    static final String ADMIN = "admin-one@example.com", SECOND_ADMIN = "admin-two@example.com",
        UNVERIFIED_ADMIN = "admin-three" + github.salemby.tododevops.support.CapturedVerificationMail.UNVERIFIED_DOMAIN;
    static final ObjectMapper JSON = new ObjectMapper();
    static SharedSchemaApps apps;
    static ConfigurableApplicationContext a, b;

    /** A signed-in caller with a fresh CSRF token. */
    record User(AuthClient client, long id, String email) {
        HttpResponse<String> call(String method, String path, Object body) throws Exception {
            return client.call(method, path, body);
        }
    }

    @BeforeAll
    static void start() {
        apps = new SharedSchemaApps();
        String admins = "app.admin.emails= Admin-One@Example.com ," + SECOND_ADMIN + "," + UNVERIFIED_ADMIN;
        a = apps.start(admins);
        b = apps.start(admins);
    }

    @AfterAll
    static void stop() {
        apps.close();
    }

    @Test
    void nonAdministratorsSeeUnknownRoutes() throws Exception {
        var anonymous = new AuthClient(SharedSchemaApps.port(a));
        anonymous.csrf();
        assertEquals(401, anonymous.call("POST", "/admin/accounts/1/deactivate", null).statusCode());

        var u = user();
        var cases = new java.util.ArrayList<Object[]>();
        for (String method : List.of("POST", "PUT", "PATCH", "DELETE", "GET", "HEAD", "OPTIONS"))
            cases.add(new Object[]{method, "/admin/accounts/" + u.id() + "/deactivate", null});
        cases.add(new Object[]{"POST", "/admin/projects/1/owners", Map.of("accountId", "x")});
        cases.add(new Object[]{"POST", "/admin", null});
        for (Object[] c : cases) {
            String method = (String) c[0], path = (String) c[1];
            var hidden = u.call(method, path, c[2]);
            var unknown = u.call(method, path.replace("/admin", "/nimda"), c[2]);
            assertEquals(404, unknown.statusCode(), method + " unknown: " + unknown.body());
            assertEquals(404, hidden.statusCode(), method + " " + path + ": " + hidden.body());
            // Byte for byte, and every header but the clock and cookies: nothing may tell the routes exist.
            assertEquals(unknown.body().replace("nimda", "admin"), hidden.body(), method + " body");
            assertEquals(headers(unknown), headers(hidden), method + " headers");
        }
        assertEquals(200, u.call("GET", "/auth/me", null).statusCode(), "nothing ran against the caller");
    }

    @Test
    void administratorWritesNeedCsrf() throws Exception {
        var target = user();
        var client = new AuthClient(SharedSchemaApps.port(a));
        client.csrf();
        client.call("POST", "/auth/register", Map.of("email", ADMIN, "displayName", "Admin", "password", PASSWORD));
        assertEquals(200, client.login(ADMIN, PASSWORD).statusCode());
        // The pre-login token: login rotates it, so this request carries a stale one.
        assertEquals(403, client.call("POST", "/admin/accounts/" + target.id() + "/deactivate", null).statusCode());
        assertEquals(200, login(a, target.email()).call("GET", "/auth/me", null).statusCode());
    }

    @Test
    void deactivationEndsSessionsClearsAssignmentsAndKeepsHistory() throws Exception {
        var t = user();
        var tOnB = login(b, t.email());
        var o = user();
        long p1 = project(t);
        long k1 = task(t, p1);
        assign(t, p1, k1, t.id());
        assertEquals(201, t.call("POST", tasks(p1) + "/" + k1 + "/comments", Map.of("body", "Kept")).statusCode());
        long p2 = project(o);
        addMember(o, p2, t, "MEMBER");
        long k2 = task(o, p2);
        assign(o, p2, k2, t.id());
        assertEquals(204, o.call("DELETE", "/projects/" + p2, null).statusCode());

        var response = deactivate(admin(), t.id(), 200);
        assertFalse(response.get("account").get("active").asBoolean());
        assertEquals(t.id(), response.get("account").get("id").asLong());
        assertEquals(2, response.get("unassignedTasks").asInt());
        assertEquals(List.of(p1), ids(response.get("ownerlessProjectIds")));

        assertEquals(401, t.call("GET", "/auth/me", null).statusCode(), "session on instance A ends");
        assertEquals(401, tOnB.call("GET", "/auth/me", null).statusCode(), "session on instance B ends");
        var retry = new AuthClient(SharedSchemaApps.port(a));
        retry.csrf();
        assertEquals(401, retry.login(t.email(), PASSWORD).statusCode());

        assertTrue(read(o, tasks(p2) + "/" + k2).get("assigneeId").isNull());
        var members = read(o, "/projects/" + p2 + "/members").get("items");
        assertTrue(members.valueStream().anyMatch(m -> m.get("accountId").asLong() == t.id()), "membership kept");
        assertEquals(1, jdbc().queryForObject(
            "SELECT count(*) FROM task_comments WHERE author_account_id=? AND active", Integer.class, t.id()));

        var again = deactivate(admin(), t.id(), 200);
        assertEquals(0, again.get("unassignedTasks").asInt());
        assertEquals(List.of(p1), ids(again.get("ownerlessProjectIds")));
    }

    @Test
    void reactivationAllowsLoginButNotOldSessions() throws Exception {
        var t = user();
        long p = project(t);
        long k = task(t, p);
        assign(t, p, k, t.id());
        var admin = admin();
        deactivate(admin, t.id(), 200);
        var reactivated = admin.call("POST", "/admin/accounts/" + t.id() + "/reactivate", null);
        assertEquals(200, reactivated.statusCode(), reactivated.body());
        assertTrue(JSON.readTree(reactivated.body()).get("active").asBoolean());
        assertEquals(401, t.call("GET", "/auth/me", null).statusCode(), "old sessions stay revoked");
        var fresh = login(a, t.email());
        assertEquals(200, fresh.call("GET", "/auth/me", null).statusCode());
        assertTrue(read(fresh, tasks(p) + "/" + k).get("assigneeId").isNull(), "assignments are not restored");
        assertEquals(200, admin.call("POST", "/admin/accounts/" + t.id() + "/reactivate", null).statusCode());
    }

    @Test
    void unknownAccountsAndBadIds() throws Exception {
        var admin = admin();
        for (String action : List.of("deactivate", "reactivate")) {
            var unknown = admin.call("POST", "/admin/accounts/999999999/" + action, null);
            assertEquals(404, unknown.statusCode(), unknown.body());
            assertEquals("Account not found", JSON.readTree(unknown.body()).get("detail").stringValue());
            assertEquals(400, admin.call("POST", "/admin/accounts/abc/" + action, null).statusCode());
            assertEquals(404, admin.call("POST", "/admin/accounts/0/" + action, null).statusCode());
        }
    }

    @Test
    void lastActiveAdministratorStays() throws Exception {
        var first = admin();
        var second = register(a, SECOND_ADMIN);
        deactivate(first, second.id(), 200);
        var refused = first.call("POST", "/admin/accounts/" + first.id() + "/deactivate", null);
        assertDetail(refused, 409, "At least one active administrator is required");
        assertEquals(200, first.call("GET", "/auth/me", null).statusCode());

        assertEquals(200, first.call("POST", "/admin/accounts/" + second.id() + "/reactivate", null).statusCode());
        second = login(a, SECOND_ADMIN);
        deactivate(second, first.id(), 200);
        assertEquals(200, second.call("POST", "/admin/accounts/" + first.id() + "/reactivate", null).statusCode());
    }

    @Test
    void unverifiedAccountsAreNotAdministrators() throws Exception {
        var pending = new AuthClient(SharedSchemaApps.port(a));
        pending.csrf();
        int status = pending.register(UNVERIFIED_ADMIN).statusCode();
        assertTrue(status == 201, "registered, unverified: " + status);
        assertEquals(401, pending.login(UNVERIFIED_ADMIN, PASSWORD).statusCode());

        var first = admin();
        var second = register(a, SECOND_ADMIN);
        deactivate(first, second.id(), 200);
        // The configured but unverified third address must not count as another active administrator.
        assertDetail(first.call("POST", "/admin/accounts/" + first.id() + "/deactivate", null), 409,
            "At least one active administrator is required");
        assertEquals(200, first.call("POST", "/admin/accounts/" + second.id() + "/reactivate", null).statusCode());
    }

    @Test
    void administratorsDeactivatingEachOtherKeepOneActive() throws Exception {
        var first = admin();
        var second = register(a, SECOND_ADMIN);
        long p = project(second);
        long k = task(second, p);
        assign(second, p, k, second.id());
        var data = a.getBean(DataSource.class);
        try (var c = data.getConnection()) {
            c.setAutoCommit(false);
            try (var s = c.createStatement()) {
                // Holds the second administrator's task, so the first deactivation pauses after its check.
                s.execute("SELECT 1 FROM tasks WHERE id=" + k + " FOR UPDATE");
                var firstWins = CompletableFuture.supplyAsync(() -> call(first, "POST", "/admin/accounts/" + second.id() + "/deactivate", null));
                assertThrows(TimeoutException.class, () -> firstWins.get(1, TimeUnit.SECONDS));
                var secondTries = CompletableFuture.supplyAsync(() -> call(second, "POST", "/admin/accounts/" + first.id() + "/deactivate", null));
                assertThrows(TimeoutException.class, () -> secondTries.get(1, TimeUnit.SECONDS),
                    "the second deactivation waits for the administrator rows");
                c.commit();
                assertEquals(200, firstWins.get(15, TimeUnit.SECONDS).statusCode());
                assertDetail(secondTries.get(15, TimeUnit.SECONDS), 409, "At least one active administrator is required");
            }
        }
        assertEquals(200, first.call("GET", "/auth/me", null).statusCode(), "the first administrator stays active");
        assertEquals(200, first.call("POST", "/admin/accounts/" + second.id() + "/reactivate", null).statusCode());
    }

    @Test
    void taskEditsDoNotRestoreAClearedAssignment() throws Exception {
        var t = user();
        var o = user();
        long p = project(o);
        addMember(o, p, t, "MEMBER");
        long k = task(o, p);
        assign(o, p, k, t.id());
        var admin = admin();
        var data = a.getBean(DataSource.class);
        try (var c = data.getConnection()) {
            c.setAutoCommit(false);
            try (var s = c.createStatement()) {
                s.execute("SELECT 1 FROM tasks WHERE id=" + k + " FOR UPDATE");
                var deactivation = CompletableFuture.supplyAsync(() -> call(admin, "POST", "/admin/accounts/" + t.id() + "/deactivate", null));
                assertThrows(TimeoutException.class, () -> deactivation.get(1, TimeUnit.SECONDS));
                // The edit loads the task while it is still assigned, then waits behind the deactivation.
                var edit = CompletableFuture.supplyAsync(() -> call(o, "PUT", tasks(p) + "/" + k,
                    Map.of("title", "Edited meanwhile", "status", "PENDING")));
                assertThrows(TimeoutException.class, () -> edit.get(1, TimeUnit.SECONDS));
                c.commit();
                assertEquals(200, deactivation.get(15, TimeUnit.SECONDS).statusCode());
                var edited = edit.get(15, TimeUnit.SECONDS);
                assertEquals(200, edited.statusCode(), edited.body());
            }
        }
        var task = read(o, tasks(p) + "/" + k);
        assertEquals("Edited meanwhile", task.get("title").stringValue());
        assertTrue(task.get("assigneeId").isNull(), "the edit must not write back the cleared assignee: " + task);
    }

    @Test
    void recoveryWaitsForADeactivationOfTheNewOwner() throws Exception {
        var admin = admin();
        var gone = user();
        var x = user();
        long q = project(gone);
        deactivate(admin, gone.id(), 200);
        var data = a.getBean(DataSource.class);
        try (var c = data.getConnection()) {
            c.setAutoCommit(false);
            try (var s = c.createStatement()) {
                s.execute("SELECT 1 FROM accounts WHERE id=" + x.id() + " FOR UPDATE"); // stands in for a deactivation
                var recovery = CompletableFuture.supplyAsync(() -> call(admin, "POST", "/admin/projects/" + q + "/owners",
                    Map.of("accountId", x.id())));
                assertThrows(TimeoutException.class, () -> recovery.get(1, TimeUnit.SECONDS), "the recovery waits for the account row");
                s.executeUpdate("UPDATE accounts SET active=false, credential_version=credential_version+1 WHERE id=" + x.id());
                c.commit();
                assertDetail(recovery.get(15, TimeUnit.SECONDS), 422, "Account must be active");
            }
        }
    }

    @Test
    void assignmentAndDeactivationSerializeOnTheAccountRow() throws Exception {
        var t = user();
        var o = user();
        long p = project(o);
        addMember(o, p, t, "MEMBER");
        long k = task(o, p);
        var data = a.getBean(DataSource.class);

        // (a) A deactivation holds the account row: an assignment waits for it, then sees the inactive account.
        try (var c1 = data.getConnection()) {
            c1.setAutoCommit(false);
            try (var s = c1.createStatement()) {
                s.execute("SELECT 1 FROM accounts WHERE id=" + t.id() + " FOR UPDATE");
                var put = CompletableFuture.supplyAsync(() -> call(o, "PUT", tasks(p) + "/" + k + "/assignee", Map.of("accountId", t.id())));
                assertThrows(TimeoutException.class, () -> put.get(1, TimeUnit.SECONDS), "the assignment waits for the account row");
                s.executeUpdate("UPDATE accounts SET active=false, credential_version=credential_version+1 WHERE id=" + t.id());
                c1.commit();
                var assigned = put.get(15, TimeUnit.SECONDS);
                assertEquals(422, assigned.statusCode(), assigned.body());
            }
        }
        var admin = admin();
        assertEquals(200, admin.call("POST", "/admin/accounts/" + t.id() + "/reactivate", null).statusCode());

        // (b) An assignment holds the account row: a deactivation waits for it, then clears the new assignment.
        try (var c2 = data.getConnection()) {
            c2.setAutoCommit(false);
            try (var s = c2.createStatement()) {
                s.execute("SELECT 1 FROM accounts WHERE id=" + t.id() + " FOR SHARE");
                s.executeUpdate("UPDATE tasks SET assignee_account_id=" + t.id() + " WHERE id=" + k);
                var deactivation = CompletableFuture.supplyAsync(() -> call(admin, "POST", "/admin/accounts/" + t.id() + "/deactivate", null));
                assertThrows(TimeoutException.class, () -> deactivation.get(1, TimeUnit.SECONDS), "the deactivation waits for the account row");
                c2.commit();
                var done = deactivation.get(15, TimeUnit.SECONDS);
                assertEquals(200, done.statusCode(), done.body());
                assertEquals(1, JSON.readTree(done.body()).get("unassignedTasks").asInt());
            }
        }
        assertTrue(read(o, tasks(p) + "/" + k).get("assigneeId").isNull());
    }

    @Test
    void recoveryOnlyForOwnerlessProjects() throws Exception {
        var admin = admin();
        var o = user();
        long owned = project(o);
        assertDetail(recover(admin, owned, Map.of("accountId", o.id())), 409, "Project already has an active owner");
        assertDetail(recover(admin, 999999999L, Map.of("accountId", o.id())), 404, "Project not found");
        for (Object body : List.of(Map.of(), Map.of("accountId", "5"), Map.of("accountId", 0), Map.of("accountId", 1.5)))
            assertEquals(400, recover(admin, owned, body).statusCode(), String.valueOf(body));

        var gone = user();
        long q = project(gone);
        deactivate(admin, gone.id(), 200);
        assertDetail(recover(admin, q, Map.of("accountId", gone.id())), 422, "Account must be active");
        assertDetail(recover(admin, q, Map.of("accountId", 999999999L)), 422, "Account must be active");
    }

    @Test
    void recoveryPromotesOrAddsAnOwner() throws Exception {
        var admin = admin();
        var gone = user();
        var m = user();
        var v = user();
        long q1 = project(gone);
        addMember(gone, q1, m, "MEMBER");
        addMember(gone, q1, v, "VIEWER");
        var u = user();
        long q2 = project(gone);
        assertEquals(204, gone.call("DELETE", "/projects/" + q2, null).statusCode());
        var lead = user();
        long q3 = project(gone);
        addMember(gone, q3, lead, "MANAGER");
        assertEquals(List.of(q1, q2, q3), ids(deactivate(admin, gone.id(), 200).get("ownerlessProjectIds")));

        var promotedManager = recover(admin, q3, Map.of("accountId", lead.id()));
        assertEquals(200, promotedManager.statusCode(), promotedManager.body());
        assertEquals("OWNER", JSON.readTree(promotedManager.body()).get("role").stringValue(), "a MANAGER becomes the owner (#69)");

        var promoted = recover(admin, q1, Map.of("accountId", m.id()));
        assertEquals(200, promoted.statusCode(), promoted.body());
        assertEquals("OWNER", JSON.readTree(promoted.body()).get("role").stringValue());
        assertEquals(m.id(), JSON.readTree(promoted.body()).get("accountId").asLong());
        var managed = m.call("PUT", "/projects/" + q1 + "/members/" + v.id(), Map.of("role", "MEMBER"));
        assertEquals(200, managed.statusCode(), "the new owner manages members: " + managed.body());
        assertEquals(1, read(m, "/projects/" + q1 + "/members").get("items").valueStream()
            .filter(row -> row.get("accountId").asLong() == m.id()).count(), "promoted, not duplicated");

        var added = recover(admin, q2, Map.of("accountId", u.id()));
        assertEquals(200, added.statusCode(), added.body());
        assertEquals("OWNER", JSON.readTree(added.body()).get("role").stringValue());
        assertEquals(200, u.call("GET", "/projects/" + q2, null).statusCode(), "recovery works on archived projects");

        assertDetail(recover(admin, q1, Map.of("accountId", u.id())), 409, "Project already has an active owner");
    }

    @Test
    void deactivationRecordsUnassignsWithAdminActor() throws Exception {
        var admin = admin();
        var t = user();
        var o = user();
        long p1 = project(o), p2 = project(o);
        addMember(o, p1, t, "MEMBER");
        addMember(o, p2, t, "MEMBER");
        long k1 = task(o, p1), k2 = task(o, p2);
        assign(o, p1, k1, t.id());
        assign(o, p2, k2, t.id());

        deactivate(admin, t.id(), 200);
        for (long[] pair : new long[][]{{p1, k1}, {p2, k2}}) {
            var newest = read(o, "/projects/" + pair[0] + "/activity").get("items").get(0);
            assertEquals("TASK", newest.get("resourceType").stringValue());
            assertEquals(pair[1], newest.get("resourceId").asLong());
            assertEquals("UPDATED", newest.get("action").stringValue());
            assertEquals(admin.id(), newest.get("actorId").asLong(), "the administrator is the actor");
            assertEquals(List.of("assigneeId:" + t.id() + ">null"), changes(newest));
        }
    }

    @Test
    void ownerRecoveryIsRecorded() throws Exception {
        var admin = admin();
        var gone = user();
        var m = user();
        var u = user();
        long q1 = project(gone), q2 = project(gone);
        addMember(gone, q1, m, "MEMBER");
        deactivate(admin, gone.id(), 200);

        assertEquals(200, recover(admin, q1, Map.of("accountId", m.id())).statusCode());
        var promoted = read(m, "/projects/" + q1 + "/activity").get("items").get(0);
        assertEquals(List.of("MEMBER", "UPDATED"), List.of(promoted.get("resourceType").stringValue(), promoted.get("action").stringValue()));
        assertEquals(m.id(), promoted.get("resourceId").asLong());
        assertEquals(admin.id(), promoted.get("actorId").asLong());
        assertEquals(List.of("role:MEMBER>OWNER"), changes(promoted));

        assertEquals(200, recover(admin, q2, Map.of("accountId", u.id())).statusCode());
        var added = read(u, "/projects/" + q2 + "/activity").get("items").get(0);
        assertEquals(List.of("MEMBER", "ADDED"), List.of(added.get("resourceType").stringValue(), added.get("action").stringValue()));
        assertEquals(u.id(), added.get("resourceId").asLong());
        assertEquals(admin.id(), added.get("actorId").asLong());
        assertEquals(List.of("role:null>OWNER"), changes(added));
    }

    // --- fixtures -------------------------------------------------------------------------------

    /** Each change of an activity event as "field:before>after", with null for no value. */
    static List<String> changes(JsonNode event) {
        return event.get("changes").valueStream().map(c -> c.get("field").stringValue() + ":"
            + (c.get("before").isNull() ? "null" : c.get("before").stringValue()) + ">"
            + (c.get("after").isNull() ? "null" : c.get("after").stringValue())).toList();
    }

    static Map<String, List<String>> headers(HttpResponse<String> response) {
        var all = new java.util.TreeMap<>(response.headers().map());
        all.remove("date");
        all.remove("set-cookie");
        return all;
    }

    static HttpResponse<String> recover(User admin, long projectId, Object body) throws Exception {
        return admin.call("POST", "/admin/projects/" + projectId + "/owners", body);
    }

    static void assertDetail(HttpResponse<String> response, int status, String detail) throws Exception {
        assertEquals(status, response.statusCode(), response.body());
        assertEquals(detail, JSON.readTree(response.body()).get("detail").stringValue());
    }

    static User admin() throws Exception {
        return register(a, ADMIN);
    }

    static User user() throws Exception {
        return register(a, "user-" + UUID.randomUUID() + "@example.com");
    }

    /** Registers the email if it is new, then logs in on that instance. */
    static User register(ConfigurableApplicationContext app, String email) throws Exception {
        var client = new AuthClient(SharedSchemaApps.port(app));
        client.csrf();
        int status = client.register(email).statusCode();
        assertTrue(status == 201 || status == 409, "register " + email + ": " + status);
        return login(app, email);
    }

    static User login(ConfigurableApplicationContext app, String email) throws Exception {
        var client = new AuthClient(SharedSchemaApps.port(app));
        client.csrf();
        var login = client.login(email, PASSWORD);
        assertEquals(200, login.statusCode(), login.body());
        client.csrf(); // login rotates the token
        return new User(client, JSON.readTree(login.body()).get("id").asLong(), email);
    }

    static long project(User owner) throws Exception {
        String key = "A" + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
        var created = owner.call("POST", "/projects", Map.of("name", "Admin fixture", "key", key));
        assertEquals(201, created.statusCode(), created.body());
        return JSON.readTree(created.body()).get("id").asLong();
    }

    static long task(User caller, long projectId) throws Exception {
        var created = caller.call("POST", tasks(projectId), Map.of("title", "Assigned work"));
        assertEquals(201, created.statusCode(), created.body());
        return JSON.readTree(created.body()).get("id").asLong();
    }

    static void assign(User caller, long projectId, long taskId, long accountId) throws Exception {
        var assigned = caller.call("PUT", tasks(projectId) + "/" + taskId + "/assignee", Map.of("accountId", accountId));
        assertEquals(200, assigned.statusCode(), assigned.body());
    }

    static void addMember(User owner, long projectId, User member, String role) throws Exception {
        var added = owner.call("POST", "/projects/" + projectId + "/members", Map.of("email", member.email(), "role", role));
        assertEquals(201, added.statusCode(), added.body());
    }

    static JsonNode deactivate(User admin, long accountId, int status) throws Exception {
        var response = admin.call("POST", "/admin/accounts/" + accountId + "/deactivate", null);
        assertEquals(status, response.statusCode(), response.body());
        return JSON.readTree(response.body());
    }

    static JsonNode read(User caller, String path) throws Exception {
        var response = caller.call("GET", path, null);
        assertEquals(200, response.statusCode(), response.body());
        return JSON.readTree(response.body());
    }

    static HttpResponse<String> call(User caller, String method, String path, Object body) {
        try {
            return caller.call(method, path, body);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    static List<Long> ids(JsonNode array) {
        return array.valueStream().map(JsonNode::asLong).toList();
    }

    static String tasks(long projectId) {
        return "/projects/" + projectId + "/tasks";
    }

    static JdbcTemplate jdbc() {
        return a.getBean(JdbcTemplate.class);
    }
}
