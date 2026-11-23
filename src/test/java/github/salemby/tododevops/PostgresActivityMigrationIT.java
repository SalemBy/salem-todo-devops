package github.salemby.tododevops;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

import java.sql.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/** V13: append-only activity events and their field changes (#12). */
class PostgresActivityMigrationIT {
    private static final String EVENT =
        "INSERT INTO activity_events(id,project_id,task_id,actor_account_id,resource_type,resource_id,action,occurred_at) VALUES ";

    private final String url = System.getenv("TEST_DB_URL"), user = System.getenv("TEST_DB_USERNAME"),
        password = System.getenv("TEST_DB_PASSWORD");

    @Test
    void v13AppliesWithExistingRowsUntouched() throws Exception {
        inSchemaAtV12((flyway, s) -> {
            var before = List.of(rows(s, "SELECT * FROM projects ORDER BY id"), rows(s, "SELECT * FROM tasks ORDER BY id"),
                rows(s, "SELECT * FROM project_memberships ORDER BY project_id, account_id"));
            flyway.migrate();
            assertEquals(before, List.of(rows(s, "SELECT * FROM projects ORDER BY id"), rows(s, "SELECT * FROM tasks ORDER BY id"),
                rows(s, "SELECT * FROM project_memberships ORDER BY project_id, account_id")));
            assertEquals(0, rows(s, "SELECT 1 FROM activity_events").size(), "no backfill");
            assertEquals(2, rows(s, "SELECT 1 FROM pg_indexes WHERE schemaname=current_schema() AND indexname IN "
                + "('idx_activity_events_project','idx_activity_events_task')").size());
            assertTrue(flyway.validateWithResult().validationSuccessful);
        });
    }

    @Test
    void unknownResourceTypeOrActionFailsCheck() throws Exception {
        inSchemaAtV12((flyway, s) -> {
            flyway.migrate();
            assertEquals("23514", state(s, EVENT + "(1,101,NULL,1,'USER',101,'CREATED',CURRENT_TIMESTAMP)"));
            assertEquals("23514", state(s, EVENT + "(1,101,NULL,1,'PROJECT',101,'MOVED',CURRENT_TIMESTAMP)"));
            assertEquals(1, s.executeUpdate(EVENT + "(1,101,201,1,'TASK',201,'UPDATED',CURRENT_TIMESTAMP)"));
        });
    }

    @Test
    void unknownActorFailsForeignKey() throws Exception {
        inSchemaAtV12((flyway, s) -> {
            flyway.migrate();
            assertEquals("23503", state(s, EVENT + "(1,101,NULL,999,'PROJECT',101,'CREATED',CURRENT_TIMESTAMP)"));
            assertEquals("23502", state(s, EVENT + "(1,101,NULL,NULL,'PROJECT',101,'CREATED',CURRENT_TIMESTAMP)"));
        });
    }

    @Test
    void deletingProjectCascadesToEventsAndChanges() throws Exception {
        inSchemaAtV12((flyway, s) -> {
            flyway.migrate();
            s.executeUpdate(EVENT + "(1,101,201,1,'TASK',201,'UPDATED',CURRENT_TIMESTAMP)");
            s.executeUpdate("INSERT INTO activity_changes(event_id,position,field,before_value,after_value) VALUES "
                + "(1,0,'status','PENDING','COMPLETED')");
            s.executeUpdate("DELETE FROM tasks WHERE id=201");
            s.executeUpdate("DELETE FROM project_memberships WHERE project_id=101");
            s.executeUpdate("DELETE FROM projects WHERE id=101");
            assertEquals(0, rows(s, "SELECT 1 FROM activity_events").size());
            assertEquals(0, rows(s, "SELECT 1 FROM activity_changes").size());
        });
    }

    interface SchemaCheck { void run(Flyway flyway, Statement statement) throws Exception; }

    /** A fresh schema at V12 with one account, project, membership and task, then the check. */
    private void inSchemaAtV12(SchemaCheck check) throws Exception {
        String schema = "activity_" + UUID.randomUUID().toString().replace("-", "");
        var flyway = Flyway.configure().dataSource(url, user, password).schemas(schema).cleanDisabled(false).load();
        try {
            Flyway.configure().dataSource(url, user, password).schemas(schema).target("12").load().migrate();
            try (var c = DriverManager.getConnection(url, user, password)) {
                c.setSchema(schema);
                try (var s = c.createStatement()) {
                    s.executeUpdate("INSERT INTO accounts(id,email,display_name,password_hash,created_at,updated_at,email_verified_at) VALUES "
                        + "(1,'owner@example.com','Owner','test-fixture-hash',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
                    s.executeUpdate("INSERT INTO projects(id,name,project_key,created_at,updated_at,created_by_account_id) VALUES "
                        + "(101,'One','ONE',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,1)");
                    s.executeUpdate("INSERT INTO project_memberships(project_id,account_id,role,created_at,updated_at) VALUES "
                        + "(101,1,'OWNER',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
                    s.executeUpdate("INSERT INTO tasks(id,project_id,title,status,created_at,updated_at) VALUES "
                        + "(201,101,'Track','PENDING',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
                    check.run(flyway, s);
                }
            }
        } finally {
            flyway.clean();
        }
    }

    private static String state(Statement s, String sql) {
        return assertThrows(SQLException.class, () -> s.executeUpdate(sql)).getSQLState();
    }

    private static List<List<Object>> rows(Statement s, String sql) throws SQLException {
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
