package fr.vriege.anilib.platform.desktopextensionhost.compat.android.widget;

import fr.vriege.anilib.platform.desktopextensionhost.compat.android.content.Context;

public final class Toast {
    public static final int LENGTH_SHORT = 0;
    public static final int LENGTH_LONG = 1;

    private Toast() {
    }

    public static Toast makeText(Context context, CharSequence text, int duration) {
        return new Toast();
    }

    public void show() {
    }
}
