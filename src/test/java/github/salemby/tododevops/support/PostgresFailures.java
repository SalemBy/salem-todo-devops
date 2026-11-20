package github.salemby.tododevops.support;

import org.postgresql.util.PSQLException;
import org.postgresql.util.ServerErrorMessage;
import org.springframework.dao.DataIntegrityViolationException;

/** Builds the exception chain a real run produces when PostgreSQL sends localized messages. */
public final class PostgresFailures {
    private PostgresFailures() {}

    public static DataIntegrityViolationException integrityViolation(String sqlState, String constraint) {
        // Wire format: one-letter field code, value, NUL. "n" is the constraint name.
        String fields = "SERRO\0C" + sqlState + "\0Mmensagem localizada\0" + (constraint == null ? "" : "n" + constraint + "\0");
        var postgres = new PSQLException(new ServerErrorMessage(fields));
        // Hibernate leaves the name null because it parses English message text only.
        var hibernate = new org.hibernate.exception.ConstraintViolationException("could not execute statement", postgres, null);
        return new DataIntegrityViolationException("could not execute statement", hibernate);
    }
}
