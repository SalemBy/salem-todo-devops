package github.salemby.tododevops;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PostgresMigrationIT {
    @Test
    void upgradesExistingTasksAndEnforcesConstraints() throws Exception {
        String url = requiredEnvironment("TEST_DB_URL");
        String username = requiredEnvironment("TEST_DB_USERNAME");
        String password = requiredEnvironment("TEST_DB_PASSWORD");
        // Only this randomly named test schema is migrated and cleaned.
        String schema = "migration_test_" + UUID.randomUUID().toString().replace("-", "");
        Flyway flyway = Flyway.configure().dataSource(url, username, password)
            .schemas(schema).target("3").cleanDisabled(false).load();
        try {
            Flyway.configure().dataSource(url, username, password).schemas(schema)
                .target("1").load().migrate();
            try (var connection = DriverManager.getConnection(url, username, password)) {
                assertEquals("PostgreSQL", connection.getMetaData().getDatabaseProductName());
                connection.setSchema(schema);
                try (var statement = connection.createStatement()) {
                    statement.executeUpdate("""
                        INSERT INTO tasks (title, description, status, created_at, updated_at)
                        VALUES ('Before migration', 'Preserved details', 'COMPLETED',
                            '2025-01-01T00:00:00Z', '2025-01-02T00:00:00Z')
                        """);
                    flyway.migrate();
                    try (var result = statement.executeQuery("SELECT * FROM tasks")) {
                        assertTrue(result.next());
                        assertEquals(1L, result.getLong("id"));
                        assertEquals("Before migration", result.getString("title"));
                        assertEquals("Preserved details", result.getString("description"));
                        assertEquals("COMPLETED", result.getString("status"));
                        assertTrue(result.getBoolean("active"));
                        assertEquals(Instant.parse("2025-01-01T00:00:00Z"), result.getTimestamp("created_at").toInstant());
                        assertEquals(Instant.parse("2025-01-02T00:00:00Z"), result.getTimestamp("updated_at").toInstant());
                        assertFalse(result.next());
                    }
                    SQLException nullActive = assertThrows(SQLException.class,
                        () -> statement.executeUpdate("UPDATE tasks SET active = NULL"));
                    assertEquals("23502", nullActive.getSQLState());
                    SQLException invalidStatus = assertThrows(SQLException.class,
                        () -> statement.executeUpdate("UPDATE tasks SET status = 'INVALID'"));
                    assertEquals("23514", invalidStatus.getSQLState());
                }
            }
            assertTrue(flyway.validateWithResult().validationSuccessful);
        } finally {
            flyway.clean();
        }
    }

    private String requiredEnvironment(String name) {
        String value = System.getenv(name);
        assertNotNull(value, name + " must point to an isolated PostgreSQL test database");
        assertFalse(value.isBlank(), name + " must not be blank");
        return value;
    }

    @Test
    void createsProjectsFromV2WithoutChangingExistingTasks() throws Exception {
        String url = requiredEnvironment("TEST_DB_URL");
        String username = requiredEnvironment("TEST_DB_USERNAME");
        String password = requiredEnvironment("TEST_DB_PASSWORD");
        String schema = "project_upgrade_" + UUID.randomUUID().toString().replace("-", "");
        Flyway flyway = Flyway.configure().dataSource(url, username, password)
            .schemas(schema).target("3").cleanDisabled(false).load();
        try {
            Flyway.configure().dataSource(url, username, password).schemas(schema)
                .target("2").load().migrate();
            try (var connection = DriverManager.getConnection(url, username, password)) {
                connection.setSchema(schema);
                try (var statement = connection.createStatement()) {
                    statement.executeUpdate("""
                        INSERT INTO tasks (id, title, description, status, active, created_at, updated_at)
                        VALUES (100, 'Active task', 'Details', 'IN_PROGRESS', TRUE,
                                '2025-01-01T00:00:00Z', '2025-01-02T00:00:00Z'),
                               (101, 'Inactive task', NULL, 'COMPLETED', FALSE,
                                '2025-02-01T00:00:00Z', '2025-02-02T00:00:00Z')
                        """);
                    var before = new java.util.ArrayList<java.util.List<Object>>();
                    try (var result = statement.executeQuery("SELECT * FROM tasks ORDER BY id")) {
                        while (result.next()) {
                            var row = new java.util.ArrayList<Object>();
                            for (int column = 1; column <= result.getMetaData().getColumnCount(); column++) {
                                row.add(result.getObject(column));
                            }
                            before.add(row);
                        }
                    }
                    flyway.migrate();
                    var after = new java.util.ArrayList<java.util.List<Object>>();
                    try (var result = statement.executeQuery("SELECT * FROM tasks ORDER BY id")) {
                        while (result.next()) {
                            var row = new java.util.ArrayList<Object>();
                            for (int column = 1; column <= result.getMetaData().getColumnCount(); column++) {
                                row.add(result.getObject(column));
                            }
                            after.add(row);
                        }
                    }
                    assertEquals(before, after, "Project migration must preserve every task column");
                    try (var result = statement.executeQuery("SELECT count(*) FROM projects")) {
                        assertTrue(result.next());
                        assertEquals(0, result.getLong(1));
                    }
                    statement.executeUpdate("""
                        INSERT INTO projects (name, project_key, created_at, updated_at)
                        VALUES ('Project', 'KEY', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                        """);
                    try (var result = statement.executeQuery("SELECT active FROM projects WHERE project_key = 'KEY'")) {
                        assertTrue(result.next());
                        assertTrue(result.getBoolean(1));
                    }
                    statement.executeUpdate("UPDATE projects SET active = FALSE WHERE project_key = 'KEY'");
                    SQLException duplicate = assertThrows(SQLException.class, () -> statement.executeUpdate("""
                        INSERT INTO projects (name, project_key, created_at, updated_at)
                        VALUES ('Duplicate', 'KEY', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                        """));
                    assertEquals("23505", duplicate.getSQLState());
                    SQLException nullActive = assertThrows(SQLException.class,
                        () -> statement.executeUpdate("UPDATE projects SET active = NULL"));
                    assertEquals("23502", nullActive.getSQLState());
                }
            }
            assertTrue(flyway.validateWithResult().validationSuccessful);
        } finally {
            flyway.clean();
        }
    }
}
