package fr.vriege.anilib.platform.desktop;

import java.nio.file.Path;
import java.util.Locale;

/** Resolves the platform-owned default data directory without leaking it inward. */
final class DesktopDataDirectory {
    private DesktopDataDirectory() {
    }

    static Path resolve() {
        String operatingSystem = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (operatingSystem.contains("win")) {
            String localAppData = System.getenv("LOCALAPPDATA");
            if (localAppData != null && !localAppData.isBlank()) {
                return Path.of(localAppData, "Anilib");
            }
        }
        if (operatingSystem.contains("mac")) {
            return Path.of(System.getProperty("user.home"), "Library", "Application Support", "Anilib");
        }
        String xdgDataHome = System.getenv("XDG_DATA_HOME");
        if (xdgDataHome != null && !xdgDataHome.isBlank()) {
            return Path.of(xdgDataHome, "anilib");
        }
        return Path.of(System.getProperty("user.home"), ".local", "share", "anilib");
    }
}
