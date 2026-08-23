package fr.vriege.anilib.feature.applicationupdate;

import java.util.Locale;

public enum ApplicationPlatform {
    WINDOWS,
    LINUX,
    MACOS,
    UNKNOWN;

    public static ApplicationPlatform current() {
        String operatingSystem = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (operatingSystem.contains("win")) {
            return WINDOWS;
        }
        if (operatingSystem.contains("mac")) {
            return MACOS;
        }
        if (operatingSystem.contains("linux")) {
            return LINUX;
        }
        return UNKNOWN;
    }
}
