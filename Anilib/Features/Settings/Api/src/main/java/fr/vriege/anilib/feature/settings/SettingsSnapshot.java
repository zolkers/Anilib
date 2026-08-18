package fr.vriege.anilib.feature.settings;

import java.util.Objects;

public record SettingsSnapshot(
        ThemeMode themeMode,
        StartScreen startScreen,
        boolean showAdultContent,
        boolean incognitoMode,
        boolean downloadOnlyOnWifi,
        boolean updateOnlyOnWifi) {
    public SettingsSnapshot {
        Objects.requireNonNull(themeMode, "themeMode must not be null");
        Objects.requireNonNull(startScreen, "startScreen must not be null");
    }

    public static SettingsSnapshot defaults() {
        return new SettingsSnapshot(ThemeMode.SYSTEM, StartScreen.LIBRARY, false, false, true, true);
    }

    public SettingsSnapshot withThemeMode(ThemeMode value) {
        return new SettingsSnapshot(
                value, startScreen, showAdultContent, incognitoMode, downloadOnlyOnWifi, updateOnlyOnWifi);
    }

    public SettingsSnapshot withStartScreen(StartScreen value) {
        return new SettingsSnapshot(
                themeMode, value, showAdultContent, incognitoMode, downloadOnlyOnWifi, updateOnlyOnWifi);
    }

    public SettingsSnapshot withShowAdultContent(boolean value) {
        return new SettingsSnapshot(
                themeMode, startScreen, value, incognitoMode, downloadOnlyOnWifi, updateOnlyOnWifi);
    }

    public SettingsSnapshot withIncognitoMode(boolean value) {
        return new SettingsSnapshot(
                themeMode, startScreen, showAdultContent, value, downloadOnlyOnWifi, updateOnlyOnWifi);
    }

    public SettingsSnapshot withDownloadOnlyOnWifi(boolean value) {
        return new SettingsSnapshot(
                themeMode, startScreen, showAdultContent, incognitoMode, value, updateOnlyOnWifi);
    }

    public SettingsSnapshot withUpdateOnlyOnWifi(boolean value) {
        return new SettingsSnapshot(
                themeMode, startScreen, showAdultContent, incognitoMode, downloadOnlyOnWifi, value);
    }
}
