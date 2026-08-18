package fr.vriege.anilib.feature.applicationupdate;

import java.util.Locale;

public enum ApplicationPlatform {
    ANDROID,
    WINDOWS,
    LINUX,
    MACOS,
    UNKNOWN;

    public static ApplicationPlatform current() {
        String runtime = System.getProperty("java.runtime.name", "").toLowerCase(Locale.ROOT);
        if (runtime.contains("android")) {
            return ANDROID;
        }
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
