package github.salemby.tododevops;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

import java.sql.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/** V9: an optional task assignee that the database keeps within the task's project members (#10). */
class PostgresTaskAssignmentMigrationIT {
    private final String url = System.getenv("TEST_DB_URL"), user = System.getenv("TEST_DB_USERNAME"),
        password = System.getenv("TEST_DB_PASSWORD");

    @Test
    void existingTasksKeepTheirDataWithNoAssignee() throws Exception {
        inSchemaAtV8((flyway, s) -> {
            var tasks = rows(s, "SELECT * FROM tasks ORDER BY id");
            flyway.migrate();
            var after = rows(s, "SELECT id, project_id, title, assignee_account_id FROM tasks ORDER BY id");
            assertEquals(tasks.size(), after.size());
            assertEquals(Arrays.asList(201L, 101L, "Keep", null), after.get(0));
            assertEquals(1, rows(s, "SELECT 1 FROM pg_indexes WHERE schemaname=current_schema() AND indexname='idx_tasks_project_assignee'").size());
            assertTrue(flyway.validateWithResult().validationSuccessful);
        });
    }

    @Test
    void assigneeMustBeAMemberOfTheTasksProject() throws Exception {
        inSchemaAtV8((flyway, s) -> {
            flyway.migrate();
            // Account 3 is a member of project 102 only; account 999 does not exist.
            var failure = assertThrows(SQLException.class,
                () -> s.executeUpdate("UPDATE tasks SET assignee_account_id=3 WHERE id=201"));
            assertEquals("23503", failure.getSQLState());
            assertTrue(failure.getMessage().contains("fk_tasks_assignee_membership"), failure.getMessage());
            assertEquals("23503", state(s, "UPDATE tasks SET assignee_account_id=999 WHERE id=201"));
            assertEquals(1, s.executeUpdate("UPDATE tasks SET assignee_account_id=2 WHERE id=201"));
            assertEquals(1, s.executeUpdate("UPDATE tasks SET assignee_account_id=NULL WHERE id=201"));
        });
    }

    @Test
    void assignedMembershipCannotBeDeletedDirectly() throws Exception {
        inSchemaAtV8((flyway, s) -> {
            flyway.migrate();
            s.executeUpdate("UPDATE tasks SET assignee_account_id=2 WHERE id=201");
            assertEquals("23503", state(s, "DELETE FROM project_memberships WHERE project_id=101 AND account_id=2"));
            assertEquals(1, rows(s, "SELECT 1 FROM project_memberships WHERE project_id=101 AND account_id=2").size());
        });
    }

    interface SchemaCheck { void run(Flyway flyway, Statement statement) throws Exception; }

    /** A fresh schema at V8 with two projects, three accounts, memberships and one task, then the check. */
    private void inSchemaAtV8(SchemaCheck check) throws Exception {
        String schema = "assignment_" + UUID.randomUUID().toString().replace("-", "");
        var flyway = Flyway.configure().dataSource(url, user, password).schemas(schema).cleanDisabled(false).load();
        try {
            Flyway.configure().dataSource(url, user, password).schemas(schema).target("8").load().migrate();
            try (var c = DriverManager.getConnection(url, user, password)) {
                c.setSchema(schema);
                try (var s = c.createStatement()) {
                    s.executeUpdate("INSERT INTO accounts(id,email,display_name,password_hash,created_at,updated_at) VALUES "
                        + "(1,'owner@example.com','Owner','test-fixture-hash',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP),"
                        + "(2,'member@example.com','Member','test-fixture-hash',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP),"
                        + "(3,'other@example.com','Other','test-fixture-hash',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
                    s.executeUpdate("INSERT INTO projects(id,name,project_key,created_at,updated_at,created_by_account_id) VALUES "
                        + "(101,'One','ONE',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,1),(102,'Two','TWO',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,1)");
                    s.executeUpdate("INSERT INTO project_memberships(project_id,account_id,role,created_at,updated_at) VALUES "
                        + "(101,1,'OWNER',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP),(101,2,'MEMBER',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP),"
                        + "(102,1,'OWNER',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP),(102,3,'MEMBER',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
                    s.executeUpdate("INSERT INTO tasks(id,project_id,title,status,created_at,updated_at) VALUES "
                        + "(201,101,'Keep','PENDING',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
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
