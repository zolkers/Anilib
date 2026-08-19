package fr.vriege.anilib.platform.desktopengine.server;

record DesktopEngineCapabilities(
        boolean apkInstallation,
        boolean sourceCatalogue,
        boolean mangaReading,
        boolean animeStreaming,
        boolean preferences) {

    static DesktopEngineCapabilities bootstrap() {
        return new DesktopEngineCapabilities(false, false, false, false, false);
    }

    String toJson() {
        return "{\"apkInstallation\":" + apkInstallation
                + ",\"sourceCatalogue\":" + sourceCatalogue
                + ",\"mangaReading\":" + mangaReading
                + ",\"animeStreaming\":" + animeStreaming
                + ",\"preferences\":" + preferences + '}';
    }
}
