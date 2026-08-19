package fr.vriege.anilib.platform.desktopextensionhost.server;

record DesktopExtensionHostCapabilities(
        boolean apkInstallation,
        boolean sourceCatalogue,
        boolean mangaReading,
        boolean animeStreaming,
        boolean preferences) {

    static DesktopExtensionHostCapabilities bootstrap() {
        return new DesktopExtensionHostCapabilities(true, true, false, false, false);
    }

    String toJson() {
        return "{\"apkInstallation\":" + apkInstallation
                + ",\"sourceCatalogue\":" + sourceCatalogue
                + ",\"mangaReading\":" + mangaReading
                + ",\"animeStreaming\":" + animeStreaming
                + ",\"preferences\":" + preferences + '}';
    }
}
