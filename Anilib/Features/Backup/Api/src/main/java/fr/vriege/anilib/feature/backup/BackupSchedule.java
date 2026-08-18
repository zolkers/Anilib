package fr.vriege.anilib.feature.backup;

import java.time.Duration;

public enum BackupSchedule {
    MANUAL(Duration.ZERO),
    DAILY(Duration.ofDays(1)),
    WEEKLY(Duration.ofDays(7));

    private final Duration interval;

    BackupSchedule(Duration interval) {
        this.interval = interval;
    }

    public Duration interval() {
        return interval;
    }
}
