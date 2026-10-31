package fr.vriege.anilib.platform.desktopextensionhost.compat.android.content;

import java.io.File;

public class ContextWrapper extends Context {
    private final Context base;

    public ContextWrapper(Context base) {
        this.base = base;
    }

    public ContextWrapper() {
        this(null);
    }

    public Context getBaseContext() {
        return base == null ? this : base;
    }

    @Override
    public File getFilesDir() {
        return base == null ? super.getFilesDir() : base.getFilesDir();
    }

    @Override
    public File getCacheDir() {
        return base == null ? super.getCacheDir() : base.getCacheDir();
    }

    @Override
    public Context getApplicationContext() {
        return base == null ? this : base.getApplicationContext();
    }

    @Override
    public SharedPreferences getSharedPreferences(String name, int mode) {
        return base == null
                ? super.getSharedPreferences(name, mode)
                : base.getSharedPreferences(name, mode);
    }
}
