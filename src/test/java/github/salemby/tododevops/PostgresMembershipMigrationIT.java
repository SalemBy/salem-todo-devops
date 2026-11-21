package github.salemby.tododevops;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import java.sql.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class PostgresMembershipMigrationIT {
    private final String url = System.getenv("TEST_DB_URL"), user = System.getenv("TEST_DB_USERNAME"),
        password = System.getenv("TEST_DB_PASSWORD");

    @Test void refusesExistingProjectsWithoutChangingThem() throws Exception {
        String schema = "membership_" + UUID.randomUUID().toString().replace("-", "");
        var flyway = Flyway.configure().dataSource(url, user, password).schemas(schema).cleanDisabled(false).load();
        try {
            Flyway.configure().dataSource(url, user, password).schemas(schema).target("7").load().migrate();
            try (var c = DriverManager.getConnection(url, user, password)) {
                c.setSchema(schema);
                try (var s = c.createStatement()) {
                    s.executeUpdate("INSERT INTO projects(id,name,project_key,created_at,updated_at) VALUES (101,'Unowned','OLD',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
                    s.executeUpdate("INSERT INTO tasks(id,project_id,title,status,created_at,updated_at) VALUES (101,101,'Keep','PENDING',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
                    var projects = rows(s, "SELECT * FROM projects ORDER BY id");
                    var tasks = rows(s, "SELECT * FROM tasks ORDER BY id");
                    var failure = assertThrows(org.flywaydb.core.api.FlywayException.class, flyway::migrate);
                    assertTrue(failure.getMessage().contains("V8 requires an empty projects table"), failure.getMessage());
                    assertEquals(projects, rows(s, "SELECT * FROM projects ORDER BY id"));
                    assertEquals(tasks, rows(s, "SELECT * FROM tasks ORDER BY id"));
                    assertEquals(0, rows(s, "SELECT 1 FROM information_schema.tables WHERE table_schema='" + schema + "' AND table_name='project_memberships'").size());
                    assertEquals(0, rows(s, "SELECT 1 FROM information_schema.columns WHERE table_schema='" + schema + "' AND table_name='projects' AND column_name='created_by_account_id'").size());
                }
            }
        } finally { flyway.clean(); }
    }

    @Test void appliesToEmptyProjectsAndEnforcesMembershipConstraints() throws Exception {
        String schema = "membership_" + UUID.randomUUID().toString().replace("-", "");
        var flyway = Flyway.configure().dataSource(url, user, password).schemas(schema).cleanDisabled(false).load();
        try {
            Flyway.configure().dataSource(url, user, password).schemas(schema).target("7").load().migrate();
            try (var c = DriverManager.getConnection(url, user, password)) {
                c.setSchema(schema);
                try (var s = c.createStatement()) {
                    s.executeUpdate("INSERT INTO accounts(id,email,display_name,password_hash,created_at,updated_at) VALUES (1,'owner@example.com','Owner','test-fixture-hash',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP),(2,'member@example.com','Member','test-fixture-hash',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
                    // The V7 columns only: later migrations may add columns (#71 adds verification ones).
                    var accounts = rows(s, "SELECT id,email,display_name,password_hash,active,credential_version,version,created_at,updated_at FROM accounts ORDER BY id");
                    flyway.migrate();
                    assertEquals(accounts, rows(s, "SELECT id,email,display_name,password_hash,active,credential_version,version,created_at,updated_at FROM accounts ORDER BY id"), "Accounts must be preserved");

                    String project = "INSERT INTO projects(id,name,project_key,created_at,updated_at,created_by_account_id) VALUES ";
                    assertEquals("23502", state(s, "INSERT INTO projects(name,project_key,created_at,updated_at) VALUES ('No creator','NONE',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)"));
                    assertEquals("23503", state(s, project + "(100,'Ghost','GHOST',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,999)"));
                    s.executeUpdate(project + "(101,'Owned','OWNED',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,1)");

                    String member = "INSERT INTO project_memberships(project_id,account_id,role,created_at,updated_at) VALUES ";
                    s.executeUpdate(member + "(101,1,'OWNER',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP),(101,2,'VIEWER',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
                    assertEquals("23505", state(s, member + "(101,2,'MEMBER',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)"));
                    assertEquals("23514", state(s, "UPDATE project_memberships SET role='ADMIN' WHERE account_id=2"));
                    assertEquals("23514", state(s, "UPDATE project_memberships SET role='owner' WHERE account_id=2"));
                    assertEquals("23502", state(s, "UPDATE project_memberships SET role=NULL WHERE account_id=2"));
                    assertEquals("23503", state(s, member + "(101,999,'MEMBER',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)"));
                    assertEquals("23503", state(s, member + "(999,1,'MEMBER',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)"));
                    assertEquals("23503", state(s, "DELETE FROM accounts WHERE id=2"), "Accounts with memberships cannot be deleted");
                    assertEquals(1, rows(s, "SELECT indexname FROM pg_indexes WHERE schemaname='" + schema + "' AND indexname='idx_project_memberships_account'").size());

                    s.executeUpdate("DELETE FROM projects WHERE id=101");
                    assertEquals(0, rows(s, "SELECT * FROM project_memberships").size(), "Memberships must not outlive their project");
                }
            }
            assertTrue(flyway.validateWithResult().validationSuccessful);
        } finally { flyway.clean(); }
    }

    private String state(Statement s, String sql) {
        return assertThrows(SQLException.class, () -> s.executeUpdate(sql)).getSQLState();
    }

    private List<List<Object>> rows(Statement s, String sql) throws SQLException {
        var rows = new ArrayList<List<Object>>();
        try (var r = s.executeQuery(sql)) {
            while (r.next()) {
                var row = new ArrayList<Object>();
                for (int i = 1; i <= r.getMetaData().getColumnCount(); i++) row.add(r.getObject(i));
                rows.add(row);
            }
        }
        return rows;
    }
}
