package github.salemby.tododevops.shared.persistence;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/** The current instant at the precision PostgreSQL stores, so a write response reports what a later read returns. */
public final class Timestamps {
    private Timestamps() {}

    public static Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.MICROS);
    }
}
