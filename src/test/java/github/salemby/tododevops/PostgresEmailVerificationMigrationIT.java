package github.salemby.tododevops;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

import java.sql.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/** V12: email verification columns; accounts that existed before count as verified (#71). */
class PostgresEmailVerificationMigrationIT {
    private final String url = System.getenv("TEST_DB_URL"), user = System.getenv("TEST_DB_USERNAME"),
        password = System.getenv("TEST_DB_PASSWORD");

    @Test
    void existingAccountsBecomeVerified() throws Exception {
        inSchemaAtV11((flyway, s) -> {
            flyway.migrate();
            assertTrue(flyway.validateWithResult().validationSuccessful);
            assertEquals(List.of(List.of(true, true, true)), rows(s,
                "SELECT bool_and(email_verified_at = created_at), bool_and(verification_token_hash IS NULL),"
                + " bool_and(verification_token_expires_at IS NULL) FROM accounts"));
        });
    }

    @Test
    void tokenColumnsComeInPairsAndHashesAreUnique() throws Exception {
        inSchemaAtV11((flyway, s) -> {
            flyway.migrate();
            String hash = "a".repeat(64);
            assertEquals("23514", state(s, "UPDATE accounts SET verification_token_hash='" + hash + "' WHERE id=1"));
            assertEquals("23514", state(s, "UPDATE accounts SET verification_token_expires_at=CURRENT_TIMESTAMP WHERE id=1"));
            String pair = "SET verification_token_hash='" + hash + "', verification_token_expires_at=CURRENT_TIMESTAMP";
            assertEquals(1, s.executeUpdate("UPDATE accounts " + pair + " WHERE id=1"));
            assertEquals("23505", state(s, "UPDATE accounts " + pair + " WHERE id=2"));
        });
    }

    interface SchemaCheck { void run(Flyway flyway, Statement statement) throws Exception; }

    /** A fresh schema at V11 with two accounts, then the check. */
    private void inSchemaAtV11(SchemaCheck check) throws Exception {
        String schema = "verify_" + UUID.randomUUID().toString().replace("-", "");
        var flyway = Flyway.configure().dataSource(url, user, password).schemas(schema).cleanDisabled(false).load();
        try {
            Flyway.configure().dataSource(url, user, password).schemas(schema).target("11").load().migrate();
            try (var c = DriverManager.getConnection(url, user, password)) {
                c.setSchema(schema);
                try (var s = c.createStatement()) {
                    s.executeUpdate("INSERT INTO accounts(id,email,display_name,password_hash,created_at,updated_at) VALUES "
                        + "(1,'owner@example.com','Owner','test-fixture-hash',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
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
