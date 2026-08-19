package fr.vriege.anilib.platform.desktopengine.protocol;

public final class DesktopEngineProtocol {
    public static final int VERSION = 1;
    public static final String SERVICE = "anilib-desktop-engine";
    public static final String HEALTH_PATH = "/api/v1/health";
    public static final String CAPABILITIES_PATH = "/api/v1/capabilities";
    public static final String SOURCES_PATH = "/api/v1/sources";
    public static final String INSTALLED_EXTENSIONS_PATH = "/api/v1/extensions/installed";
    public static final String INSTALL_EXTENSION_PATH = "/api/v1/extensions/install";
    public static final String UNINSTALL_EXTENSION_PATH = "/api/v1/extensions/uninstall";
    public static final String EXTENSION_REPOSITORIES_PATH = "/api/v1/extensions/repos";

    private DesktopEngineProtocol() {
    }
}
