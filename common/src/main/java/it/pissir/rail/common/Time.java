package it.pissir.rail.common;

import java.time.Instant;

public final class Time {

    private Time() {
    }

    /** Data e ora correnti in formato ISO-8601, ad esempio 2026-10-26T10:15:30Z. */
    public static String now() {
        return Instant.now().toString();
    }
}
