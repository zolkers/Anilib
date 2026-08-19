package fr.vriege.anilib.platform.desktopengine.protocol;

public final class DesktopEngineProtocol {
    public static final int VERSION = 1;
    public static final String SERVICE = "anilib-desktop-engine";
    public static final String HEALTH_PATH = "/api/v1/health";
    public static final String CAPABILITIES_PATH = "/api/v1/capabilities";

    private DesktopEngineProtocol() {
    }
}
