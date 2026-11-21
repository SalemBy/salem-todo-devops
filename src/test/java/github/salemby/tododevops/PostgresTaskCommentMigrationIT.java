package github.salemby.tododevops;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

import java.sql.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/** V10: task comments, kept non-blank and bounded by the database (#11). */
class PostgresTaskCommentMigrationIT {
    private static final String COMMENT =
        "INSERT INTO task_comments(task_id,author_account_id,body,created_at,updated_at) VALUES ";

    private final String url = System.getenv("TEST_DB_URL"), user = System.getenv("TEST_DB_USERNAME"),
        password = System.getenv("TEST_DB_PASSWORD");

    @Test
    void existingTasksAreUntouched() throws Exception {
        inSchemaAtV9((flyway, s) -> {
            var tasks = rows(s, "SELECT * FROM tasks ORDER BY id");
            flyway.migrate();
            assertEquals(tasks, rows(s, "SELECT * FROM tasks ORDER BY id"));
            assertEquals(1, rows(s, "SELECT 1 FROM pg_indexes WHERE schemaname=current_schema() AND indexname='idx_task_comments_task'").size());
            assertTrue(flyway.validateWithResult().validationSuccessful);
        });
    }

    @Test
    void bodyMustBeNonBlankAndAtMost10000Characters() throws Exception {
        inSchemaAtV9((flyway, s) -> {
            flyway.migrate();
            assertEquals("23514", state(s, COMMENT + "(201,1,' ',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)"));
            assertEquals("22001", state(s, COMMENT + "(201,1,repeat('a',10001),CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)"));
            assertEquals(1, s.executeUpdate(COMMENT + "(201,1,repeat('a',10000),CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)"));
            assertEquals(List.of(List.of(true)), rows(s, "SELECT active FROM task_comments"), "comments start active");
        });
    }

    @Test
    void authorAndTaskMustExist() throws Exception {
        inSchemaAtV9((flyway, s) -> {
            flyway.migrate();
            assertEquals("23503", state(s, COMMENT + "(201,999,'Hello',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)"));
            assertEquals("23503", state(s, COMMENT + "(999,1,'Hello',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)"));
        });
    }

    @Test
    void deletingATaskDeletesItsComments() throws Exception {
        inSchemaAtV9((flyway, s) -> {
            flyway.migrate();
            s.executeUpdate(COMMENT + "(201,1,'Hello',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
            s.executeUpdate("DELETE FROM tasks WHERE id=201");
            assertEquals(0, rows(s, "SELECT 1 FROM task_comments").size());
        });
    }

    interface SchemaCheck { void run(Flyway flyway, Statement statement) throws Exception; }

    /** A fresh schema at V9 with one account, project, membership and task, then the check. */
    private void inSchemaAtV9(SchemaCheck check) throws Exception {
        String schema = "comments_" + UUID.randomUUID().toString().replace("-", "");
        var flyway = Flyway.configure().dataSource(url, user, password).schemas(schema).cleanDisabled(false).load();
        try {
            Flyway.configure().dataSource(url, user, password).schemas(schema).target("9").load().migrate();
            try (var c = DriverManager.getConnection(url, user, password)) {
                c.setSchema(schema);
                try (var s = c.createStatement()) {
                    s.executeUpdate("INSERT INTO accounts(id,email,display_name,password_hash,created_at,updated_at) VALUES "
                        + "(1,'owner@example.com','Owner','test-fixture-hash',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
                    s.executeUpdate("INSERT INTO projects(id,name,project_key,created_at,updated_at,created_by_account_id) VALUES "
                        + "(101,'One','ONE',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,1)");
                    s.executeUpdate("INSERT INTO project_memberships(project_id,account_id,role,created_at,updated_at) VALUES "
                        + "(101,1,'OWNER',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
                    s.executeUpdate("INSERT INTO tasks(id,project_id,title,status,created_at,updated_at) VALUES "
                        + "(201,101,'Discuss','PENDING',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
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
