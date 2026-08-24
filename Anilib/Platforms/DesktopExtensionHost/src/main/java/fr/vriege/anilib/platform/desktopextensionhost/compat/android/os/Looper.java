package fr.vriege.anilib.platform.desktopextensionhost.compat.android.os;

public final class Looper {
    private static final Looper MAIN = new Looper();

    private Looper() {
    }

    public static Looper getMainLooper() {
        return MAIN;
    }
}
