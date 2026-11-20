package github.salemby.tododevops.shared.persistence;

import org.postgresql.util.PSQLException;
import org.springframework.dao.DataIntegrityViolationException;

/** Detects a named unique-constraint violation in a PostgreSQL integrity failure. */
public final class UniqueConstraints {
    private UniqueConstraints() {}

    // Reads the protocol's constraint field: Hibernate's name is parsed from English text and is null for localized servers.
    public static boolean violated(DataIntegrityViolationException exception, String constraint) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof PSQLException postgres && "23505".equals(postgres.getSQLState())
                    && postgres.getServerErrorMessage() != null
                    && constraint.equals(postgres.getServerErrorMessage().getConstraint())) return true;
        }
        return false;
    }
}
