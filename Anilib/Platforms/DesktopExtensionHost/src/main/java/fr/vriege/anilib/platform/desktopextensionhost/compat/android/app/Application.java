package fr.vriege.anilib.platform.desktopextensionhost.compat.android.app;

import fr.vriege.anilib.platform.desktopextensionhost.compat.android.content.Context;
import fr.vriege.anilib.platform.desktopextensionhost.compat.android.content.ContextWrapper;

public class Application extends ContextWrapper {
    private static final Application CURRENT = new Application();

    public Application() {
        super();
    }

    @Override public Context getApplicationContext() { return this; }
    public void onCreate() { }
    public static Application create() { return CURRENT; }
}
