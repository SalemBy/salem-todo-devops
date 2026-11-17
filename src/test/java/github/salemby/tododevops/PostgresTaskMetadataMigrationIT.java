package github.salemby.tododevops;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import java.sql.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class PostgresTaskMetadataMigrationIT {
    @Test void backfillsExistingTasksWithoutChangingHistoryAndConstrainsMetadata() throws Exception {
        String url = System.getenv("TEST_DB_URL"), user = System.getenv("TEST_DB_USERNAME"), password = System.getenv("TEST_DB_PASSWORD");
        String schema = "metadata_" + UUID.randomUUID().toString().replace("-", "");
        var flyway = Flyway.configure().dataSource(url, user, password).schemas(schema).cleanDisabled(false).load();
        try {
            Flyway.configure().dataSource(url, user, password).schemas(schema).target("4").load().migrate();
            try (var connection = DriverManager.getConnection(url, user, password)) {
                connection.setSchema(schema);
                try (var statement = connection.createStatement()) {
                    statement.executeUpdate("INSERT INTO projects(name,project_key,created_at,updated_at) VALUES ('Owner','META',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
                    statement.executeUpdate("INSERT INTO tasks(title,status,active,created_at,updated_at,project_id) SELECT 'Keep history','COMPLETED',FALSE,TIMESTAMPTZ '2000-01-01 00:00:00Z',TIMESTAMPTZ '2001-01-01 00:00:00Z',id FROM projects");
                    var before = originalColumns(statement);
                    flyway.migrate();
                    assertEquals(before, originalColumns(statement));
                    try (var row = statement.executeQuery("SELECT type,priority,due_date FROM tasks")) {
                        assertTrue(row.next());
                        assertEquals("TASK", row.getString(1));
                        assertEquals("MEDIUM", row.getString(2));
                        assertNull(row.getDate(3));
                    }
                    for (String sql : List.of("UPDATE tasks SET type='INVALID'", "UPDATE tasks SET priority='INVALID'",
                            "UPDATE tasks SET due_date=DATE '10000-01-01'")) {
                        assertEquals("23514", assertThrows(SQLException.class, () -> statement.executeUpdate(sql)).getSQLState());
                    }
                    assertEquals("23502", assertThrows(SQLException.class, () -> statement.executeUpdate("UPDATE tasks SET type=NULL")).getSQLState());
                    assertEquals("23502", assertThrows(SQLException.class, () -> statement.executeUpdate("UPDATE tasks SET priority=NULL")).getSQLState());
                }
            }
        } finally { flyway.clean(); }
    }

    private List<Object> originalColumns(Statement statement) throws SQLException {
        try (var row = statement.executeQuery("SELECT id,title,description,status,active,created_at,updated_at,project_id FROM tasks")) {
            assertTrue(row.next());
            var values = new ArrayList<Object>();
            for (int i = 1; i <= 8; i++) values.add(row.getObject(i));
            return values;
        }
    }
}
