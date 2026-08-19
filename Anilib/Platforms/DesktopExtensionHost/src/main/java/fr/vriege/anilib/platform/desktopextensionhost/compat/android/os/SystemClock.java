package fr.vriege.anilib.platform.desktopextensionhost.compat.android.os;

import java.util.concurrent.TimeUnit;

public final class SystemClock {
    private SystemClock() {
    }

    public static long elapsedRealtime() {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime());
    }

    public static long elapsedRealtimeNanos() {
        return System.nanoTime();
    }

    public static long uptimeMillis() {
        return elapsedRealtime();
    }
}
