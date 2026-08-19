package fr.vriege.anilib.platform.desktopextensionhost.compat.android.content;

public abstract class Context {
    public static final int MODE_PRIVATE = 0;
    public static final int MODE_WORLD_READABLE = 1;
    public static final int MODE_MULTI_PROCESS = 4;
    public static final int BIND_AUTO_CREATE = 1;

    protected Context() {
    }

    public Context getApplicationContext() { return this; }
    public String getPackageName() { return "fr.vriege.anilib"; }
    public String getString(int resourceId) { return ""; }
    public ClassLoader getClassLoader() { return getClass().getClassLoader(); }
    public Object getSystemService(String name) { return null; }
    public Object getAssets() { return null; }
    public Object getContentResolver() { return null; }
    public Object getMainLooper() { return null; }
    public Object getTheme() { return null; }
    public void startActivity(Intent intent) { }
    public SharedPreferences getSharedPreferences(String name, int mode) {
        return SourcePreferences.forSource(name.hashCode());
    }
}
