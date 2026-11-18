package github.salemby.tododevops;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import java.sql.*;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class PostgresAccountMigrationIT {
    @Test void upgradesV6PreservingBusinessDataAndEnforcesAccountConstraints() throws Exception {
        String url = System.getenv("TEST_DB_URL"), user = System.getenv("TEST_DB_USERNAME"), password = System.getenv("TEST_DB_PASSWORD");
        String schema = "accounts_" + UUID.randomUUID().toString().replace("-", "");
        var flyway = Flyway.configure().dataSource(url, user, password).schemas(schema).target("7").cleanDisabled(false).load();
        try {
            Flyway.configure().dataSource(url, user, password).schemas(schema).target("6").load().migrate();
            try (var connection = DriverManager.getConnection(url, user, password)) {
                connection.setSchema(schema);
                try (var s = connection.createStatement()) {
                    s.executeUpdate("INSERT INTO projects(id,name,project_key,created_at,updated_at) VALUES (101,'Owner','AUTH',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
                    s.executeUpdate("INSERT INTO tasks(id,project_id,title,status,created_at,updated_at) VALUES (101,101,'Keep task','PENDING',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
                    s.executeUpdate("INSERT INTO labels(id,project_id,name,created_at,updated_at) VALUES (101,101,'Keep label',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
                    s.executeUpdate("INSERT INTO task_labels(task_id,label_id) VALUES (101,101)");
                    String before = snapshot(s);
                    flyway.migrate();
                    assertEquals(before, snapshot(s));
                    String insert = "INSERT INTO accounts(email,display_name,password_hash,active,created_at,updated_at) VALUES ";
                    s.executeUpdate(insert + "('a@example.com','Person','test-fixture-hash',FALSE,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
                    assertEquals("23505", assertThrows(SQLException.class, () -> s.executeUpdate(insert + "('a@example.com','Other','hash',TRUE,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)")).getSQLState());
                    assertEquals("23514", assertThrows(SQLException.class, () -> s.executeUpdate(insert + "('UPPER@example.com','Other','hash',TRUE,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)")).getSQLState());
                    assertEquals("23502", assertThrows(SQLException.class, () -> s.executeUpdate(insert + "('b@example.com','Other',NULL,TRUE,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)")).getSQLState());
                }
            }
        } finally { flyway.clean(); }
    }

    private String snapshot(Statement statement) throws SQLException {
        try (var rows = statement.executeQuery("SELECT (SELECT json_agg(p) FROM projects p)::text || (SELECT json_agg(t) FROM tasks t)::text || (SELECT json_agg(l) FROM labels l)::text || (SELECT json_agg(tl) FROM task_labels tl)::text")) {
            assertTrue(rows.next()); return rows.getString(1);
        }
    }
}
