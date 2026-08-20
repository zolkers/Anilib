package fr.vriege.anilib.feature.settings;

import java.util.Objects;

public record SettingsSnapshot(
        LanguagePack languagePack,
        ThemeMode themeMode,
        ThemeFamily themeFamily,
        AccentColor accentColor,
        TypographyScale typographyScale,
        boolean reducedMotion,
        NavigationStyle navigationStyle,
        PlayerWindowMode playerWindowMode,
        BrowserPolicy browserPolicy,
        StartScreen startScreen,
        boolean showAdultContent,
        boolean incognitoMode,
        boolean downloadOnlyOnWifi,
        boolean updateOnlyOnWifi) {
    public SettingsSnapshot {
        Objects.requireNonNull(languagePack, "languagePack must not be null");
        Objects.requireNonNull(themeMode, "themeMode must not be null");
        Objects.requireNonNull(themeFamily, "themeFamily must not be null");
        Objects.requireNonNull(accentColor, "accentColor must not be null");
        Objects.requireNonNull(typographyScale, "typographyScale must not be null");
        Objects.requireNonNull(navigationStyle, "navigationStyle must not be null");
        Objects.requireNonNull(playerWindowMode, "playerWindowMode must not be null");
        Objects.requireNonNull(browserPolicy, "browserPolicy must not be null");
        Objects.requireNonNull(startScreen, "startScreen must not be null");
    }

    public static SettingsSnapshot defaults() {
        return new SettingsSnapshot(
                LanguagePack.SYSTEM,
                ThemeMode.SYSTEM,
                ThemeFamily.MATERIAL,
                AccentColor.DEFAULT,
                TypographyScale.STANDARD,
                false,
                NavigationStyle.ADAPTIVE,
                PlayerWindowMode.BORDERLESS,
                BrowserPolicy.defaults(),
                StartScreen.LIBRARY,
                false,
                false,
                true,
                true);
    }

    public SettingsSnapshot withLanguagePack(LanguagePack value) {
        return copy(value, themeMode, themeFamily, accentColor, typographyScale, reducedMotion, navigationStyle,
                playerWindowMode,
                browserPolicy,
                startScreen, showAdultContent, incognitoMode, downloadOnlyOnWifi, updateOnlyOnWifi);
    }

    public SettingsSnapshot withThemeMode(ThemeMode value) {
        return copy(languagePack, value, themeFamily, accentColor, typographyScale, reducedMotion, navigationStyle,
                playerWindowMode,
                browserPolicy,
                startScreen, showAdultContent, incognitoMode, downloadOnlyOnWifi, updateOnlyOnWifi);
    }

    public SettingsSnapshot withThemeFamily(ThemeFamily value) {
        return copy(languagePack, themeMode, value, accentColor, typographyScale, reducedMotion, navigationStyle,
                playerWindowMode,
                browserPolicy,
                startScreen, showAdultContent, incognitoMode, downloadOnlyOnWifi, updateOnlyOnWifi);
    }

    public SettingsSnapshot withAccentColor(AccentColor value) {
        return copy(languagePack, themeMode, themeFamily, value, typographyScale, reducedMotion, navigationStyle,
                playerWindowMode,
                browserPolicy,
                startScreen, showAdultContent, incognitoMode, downloadOnlyOnWifi, updateOnlyOnWifi);
    }

    public SettingsSnapshot withTypographyScale(TypographyScale value) {
        return copy(languagePack, themeMode, themeFamily, accentColor, value, reducedMotion, navigationStyle,
                playerWindowMode,
                browserPolicy,
                startScreen, showAdultContent, incognitoMode, downloadOnlyOnWifi, updateOnlyOnWifi);
    }

    public SettingsSnapshot withReducedMotion(boolean value) {
        return copy(languagePack, themeMode, themeFamily, accentColor, typographyScale, value, navigationStyle,
                playerWindowMode,
                browserPolicy, startScreen, showAdultContent, incognitoMode, downloadOnlyOnWifi, updateOnlyOnWifi);
    }

    public SettingsSnapshot withNavigationStyle(NavigationStyle value) {
        return copy(languagePack, themeMode, themeFamily, accentColor, typographyScale, reducedMotion, value,
                playerWindowMode,
                browserPolicy,
                startScreen, showAdultContent, incognitoMode, downloadOnlyOnWifi, updateOnlyOnWifi);
    }

    public SettingsSnapshot withPlayerWindowMode(PlayerWindowMode value) {
        return copy(languagePack, themeMode, themeFamily, accentColor, typographyScale, reducedMotion,
                navigationStyle, value, browserPolicy, startScreen, showAdultContent, incognitoMode,
                downloadOnlyOnWifi, updateOnlyOnWifi);
    }

    public SettingsSnapshot withBrowserPolicy(BrowserPolicy value) {
        return copy(languagePack, themeMode, themeFamily, accentColor, typographyScale, reducedMotion,
                navigationStyle, playerWindowMode, value,
                startScreen, showAdultContent, incognitoMode, downloadOnlyOnWifi, updateOnlyOnWifi);
    }

    public SettingsSnapshot withStartScreen(StartScreen value) {
        return copy(languagePack, themeMode, themeFamily, accentColor, typographyScale, reducedMotion,
                navigationStyle, playerWindowMode, browserPolicy,
                value, showAdultContent, incognitoMode, downloadOnlyOnWifi, updateOnlyOnWifi);
    }

    public SettingsSnapshot withShowAdultContent(boolean value) {
        return copy(languagePack, themeMode, themeFamily, accentColor, typographyScale, reducedMotion,
                navigationStyle, playerWindowMode, browserPolicy,
                startScreen, value, incognitoMode, downloadOnlyOnWifi, updateOnlyOnWifi);
    }

    public SettingsSnapshot withIncognitoMode(boolean value) {
        return copy(languagePack, themeMode, themeFamily, accentColor, typographyScale, reducedMotion,
                navigationStyle, playerWindowMode, browserPolicy,
                startScreen, showAdultContent, value, downloadOnlyOnWifi, updateOnlyOnWifi);
    }

    public SettingsSnapshot withDownloadOnlyOnWifi(boolean value) {
        return copy(languagePack, themeMode, themeFamily, accentColor, typographyScale, reducedMotion,
                navigationStyle, playerWindowMode, browserPolicy,
                startScreen, showAdultContent, incognitoMode, value, updateOnlyOnWifi);
    }

    public SettingsSnapshot withUpdateOnlyOnWifi(boolean value) {
        return copy(languagePack, themeMode, themeFamily, accentColor, typographyScale, reducedMotion,
                navigationStyle, playerWindowMode, browserPolicy,
                startScreen, showAdultContent, incognitoMode, downloadOnlyOnWifi, value);
    }

    private static SettingsSnapshot copy(
            LanguagePack languagePack,
            ThemeMode themeMode,
            ThemeFamily themeFamily,
            AccentColor accentColor,
            TypographyScale typographyScale,
            boolean reducedMotion,
            NavigationStyle navigationStyle,
            PlayerWindowMode playerWindowMode,
            BrowserPolicy browserPolicy,
            StartScreen startScreen,
            boolean showAdultContent,
            boolean incognitoMode,
            boolean downloadOnlyOnWifi,
            boolean updateOnlyOnWifi) {
        return new SettingsSnapshot(languagePack, themeMode, themeFamily, accentColor, typographyScale, reducedMotion,
                navigationStyle, playerWindowMode, browserPolicy, startScreen, showAdultContent, incognitoMode,
                downloadOnlyOnWifi, updateOnlyOnWifi);
    }
}
