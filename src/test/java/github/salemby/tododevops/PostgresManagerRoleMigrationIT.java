package github.salemby.tododevops;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

import java.sql.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/** V11: memberships accept the MANAGER role and still reject anything else (#69). */
class PostgresManagerRoleMigrationIT {
    private static final String MEMBER =
        "INSERT INTO project_memberships(project_id,account_id,role,created_at,updated_at) VALUES ";

    private final String url = System.getenv("TEST_DB_URL"), user = System.getenv("TEST_DB_USERNAME"),
        password = System.getenv("TEST_DB_PASSWORD");

    @Test
    void existingMembershipsAreUntouchedAndManagerIsAccepted() throws Exception {
        inSchemaAtV10((flyway, s) -> {
            var memberships = rows(s, "SELECT * FROM project_memberships ORDER BY id");
            flyway.migrate();
            assertEquals(memberships, rows(s, "SELECT * FROM project_memberships ORDER BY id"));
            assertTrue(flyway.validateWithResult().validationSuccessful);
            assertEquals(1, s.executeUpdate(MEMBER + "(101,2,'MANAGER',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)"));
        });
    }

    @Test
    void unknownRolesAreStillRejected() throws Exception {
        inSchemaAtV10((flyway, s) -> {
            flyway.migrate();
            assertEquals("23514", state(s, "UPDATE project_memberships SET role='ADMIN' WHERE account_id=1"));
            assertEquals("23514", state(s, "UPDATE project_memberships SET role='manager' WHERE account_id=1"));
            assertEquals("23502", state(s, "UPDATE project_memberships SET role=NULL WHERE account_id=1"));
        });
    }

    interface SchemaCheck { void run(Flyway flyway, Statement statement) throws Exception; }

    /** A fresh schema at V10 with two accounts and a project the first one owns, then the check. */
    private void inSchemaAtV10(SchemaCheck check) throws Exception {
        String schema = "manager_" + UUID.randomUUID().toString().replace("-", "");
        var flyway = Flyway.configure().dataSource(url, user, password).schemas(schema).cleanDisabled(false).load();
        try {
            Flyway.configure().dataSource(url, user, password).schemas(schema).target("10").load().migrate();
            try (var c = DriverManager.getConnection(url, user, password)) {
                c.setSchema(schema);
                try (var s = c.createStatement()) {
                    s.executeUpdate("INSERT INTO accounts(id,email,display_name,password_hash,created_at,updated_at) VALUES "
                        + "(1,'owner@example.com','Owner','test-fixture-hash',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
                    s.executeUpdate("INSERT INTO projects(id,name,project_key,created_at,updated_at,created_by_account_id) VALUES "
                        + "(101,'One','ONE',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,1)");
                    s.executeUpdate("INSERT INTO project_memberships(project_id,account_id,role,created_at,updated_at) VALUES "
                        + "(101,1,'OWNER',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
                    s.executeUpdate("INSERT INTO accounts(id,email,display_name,password_hash,created_at,updated_at) VALUES "
                        + "(2,'manager@example.com','Manager','test-fixture-hash',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
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
