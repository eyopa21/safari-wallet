package org.example.domain;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

// Millisecond precision everywhere: what an API response shows is exactly what Postgres stores
// and what a later read returns, and it matches the spec's timestamp examples.
public final class Now {

    private Now() {
    }

    public static Instant instant() {
        return Instant.now().truncatedTo(ChronoUnit.MILLIS);
    }
}
