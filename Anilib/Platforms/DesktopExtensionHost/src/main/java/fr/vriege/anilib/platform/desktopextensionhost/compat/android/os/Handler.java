package fr.vriege.anilib.platform.desktopextensionhost.compat.android.os;

import java.util.Objects;

public final class Handler {
    private final Looper looper;

    public Handler(Looper looper) {
        this.looper = Objects.requireNonNull(looper, "looper");
    }

    public boolean post(Runnable action) {
        Objects.requireNonNull(action, "action").run();
        return true;
    }

    public Looper getLooper() {
        return looper;
    }
}
