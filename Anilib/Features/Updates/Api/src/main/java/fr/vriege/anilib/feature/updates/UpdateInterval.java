package fr.vriege.anilib.feature.updates;

import java.time.Duration;

/** Aniyomi-style automatic library update intervals. */
public enum UpdateInterval {
    MANUAL(Duration.ZERO),
    SIX_HOURS(Duration.ofHours(6)),
    TWELVE_HOURS(Duration.ofHours(12)),
    DAILY(Duration.ofDays(1)),
    EVERY_TWO_DAYS(Duration.ofDays(2)),
    WEEKLY(Duration.ofDays(7));

    private final Duration duration;

    UpdateInterval(Duration duration) {
        this.duration = duration;
    }

    public Duration duration() {
        return duration;
    }
}
