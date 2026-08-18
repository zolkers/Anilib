package fr.vriege.anilib.feature.settings;

import java.util.Objects;

public record SettingsSnapshot(
        ThemeMode themeMode,
        boolean showAdultContent,
        boolean incognitoMode,
        boolean downloadOnlyOnWifi,
        boolean updateOnlyOnWifi) {
    public SettingsSnapshot {
        Objects.requireNonNull(themeMode, "themeMode must not be null");
    }

    public static SettingsSnapshot defaults() {
        return new SettingsSnapshot(ThemeMode.SYSTEM, false, false, true, true);
    }

    public SettingsSnapshot withThemeMode(ThemeMode value) {
        return new SettingsSnapshot(value, showAdultContent, incognitoMode, downloadOnlyOnWifi, updateOnlyOnWifi);
    }

    public SettingsSnapshot withShowAdultContent(boolean value) {
        return new SettingsSnapshot(themeMode, value, incognitoMode, downloadOnlyOnWifi, updateOnlyOnWifi);
    }

    public SettingsSnapshot withIncognitoMode(boolean value) {
        return new SettingsSnapshot(themeMode, showAdultContent, value, downloadOnlyOnWifi, updateOnlyOnWifi);
    }

    public SettingsSnapshot withDownloadOnlyOnWifi(boolean value) {
        return new SettingsSnapshot(themeMode, showAdultContent, incognitoMode, value, updateOnlyOnWifi);
    }

    public SettingsSnapshot withUpdateOnlyOnWifi(boolean value) {
        return new SettingsSnapshot(themeMode, showAdultContent, incognitoMode, downloadOnlyOnWifi, value);
    }
}
