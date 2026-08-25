package fr.vriege.anilib.platform.desktopextensionhost.compat.android.os;

import fr.vriege.anilib.framework.concurrent.runtime.ManagedExecutors;
import java.util.Objects;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class Looper {
    private static final Looper MAIN = new Looper();
    private final ScheduledExecutorService queue =
            ManagedExecutors.scheduled("anilib-android-main-looper");

    private Looper() {
    }

    public static Looper getMainLooper() {
        return MAIN;
    }

    boolean enqueue(Runnable action, long delayMillis) {
        Runnable message = Objects.requireNonNull(action, "action");
        try {
            queue.schedule(message, Math.max(0L, delayMillis), TimeUnit.MILLISECONDS);
            return true;
        } catch (RejectedExecutionException ignored) {
            return false;
        }
    }
}
