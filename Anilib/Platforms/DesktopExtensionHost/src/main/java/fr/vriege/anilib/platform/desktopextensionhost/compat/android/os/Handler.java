package fr.vriege.anilib.platform.desktopextensionhost.compat.android.os;

import java.util.Objects;

public final class Handler {
    private final Looper looper;

    public Handler(Looper looper) {
        this.looper = Objects.requireNonNull(looper, "looper");
    }

    public boolean post(Runnable action) {
        return looper.enqueue(action, 0L);
    }

    public boolean postDelayed(Runnable action, long delayMillis) {
        return looper.enqueue(action, delayMillis);
    }

    public Looper getLooper() {
        return looper;
    }
}
