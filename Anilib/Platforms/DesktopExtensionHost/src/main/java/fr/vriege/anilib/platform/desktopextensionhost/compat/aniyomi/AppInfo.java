package fr.vriege.anilib.platform.desktopextensionhost.compat.aniyomi;

public final class AppInfo {
    public static final AppInfo INSTANCE = new AppInfo();

    private AppInfo() {
    }

    public String getVersionName() {
        return "Anilib";
    }
}
