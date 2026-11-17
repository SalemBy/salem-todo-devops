package github.salemby.tododevops;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import java.sql.*;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class PostgresLabelMigrationIT {
    @Test void preservesTaskMetadataAndEnforcesLabelConstraints() throws Exception {
        String url = System.getenv("TEST_DB_URL"), user = System.getenv("TEST_DB_USERNAME"), password = System.getenv("TEST_DB_PASSWORD");
        String schema = "labels_" + UUID.randomUUID().toString().replace("-", "");
        var flyway = Flyway.configure().dataSource(url, user, password).schemas(schema).cleanDisabled(false).load();
        try {
            Flyway.configure().dataSource(url, user, password).schemas(schema).target("5").load().migrate();
            try (var connection = DriverManager.getConnection(url, user, password)) {
                connection.setSchema(schema);
                try (var s = connection.createStatement()) {
                    s.executeUpdate("INSERT INTO projects(id,name,project_key,created_at,updated_at) VALUES (101,'Owner','LABELS',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP),(102,'Other','OTHER',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
                    s.executeUpdate("INSERT INTO tasks(id,project_id,title,status,type,priority,due_date,created_at,updated_at) VALUES (101,101,'Preserve','PENDING','CHORE','HIGH',DATE '2024-02-29',TIMESTAMPTZ '2000-01-01 00:00:00Z',TIMESTAMPTZ '2000-01-02 00:00:00Z')");
                    String before = snapshot(s);
                    flyway.migrate();
                    assertEquals(before, snapshot(s));
                    s.executeUpdate("INSERT INTO labels(id,project_id,name,active,created_at,updated_at) VALUES (101,101,'Backend',FALSE,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
                    assertEquals("23505", assertThrows(SQLException.class, () -> s.executeUpdate("INSERT INTO labels(project_id,name,created_at,updated_at) VALUES (101,'BACKEND',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)")).getSQLState());
                    s.executeUpdate("INSERT INTO labels(id,project_id,name,created_at,updated_at) VALUES (102,102,'BACKEND',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
                    assertEquals("23503", assertThrows(SQLException.class, () -> s.executeUpdate("INSERT INTO labels(project_id,name,created_at,updated_at) VALUES (999,'Missing',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)")).getSQLState());
                    s.executeUpdate("INSERT INTO task_labels(task_id,label_id) VALUES (101,101)");
                    assertEquals("23505", assertThrows(SQLException.class, () -> s.executeUpdate("INSERT INTO task_labels(task_id,label_id) VALUES (101,101)")).getSQLState());
                    assertEquals("23503", assertThrows(SQLException.class, () -> s.executeUpdate("INSERT INTO task_labels(task_id,label_id) VALUES (101,999)")).getSQLState());
                    assertEquals("23503", assertThrows(SQLException.class, () -> s.executeUpdate("DELETE FROM labels WHERE id=101")).getSQLState());
                }
            }
        } finally { flyway.clean(); }
    }

    private String snapshot(Statement statement) throws SQLException {
        try (var row = statement.executeQuery("SELECT row_to_json(t)::text FROM tasks t WHERE id=101")) {
            assertTrue(row.next());
            return row.getString(1);
        }
    }
}
