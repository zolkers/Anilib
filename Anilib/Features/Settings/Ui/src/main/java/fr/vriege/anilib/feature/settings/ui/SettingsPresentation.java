package fr.vriege.anilib.feature.settings.ui;

import fr.vriege.anilib.feature.settings.SettingsSnapshot;
import fr.vriege.anilib.feature.settings.AccentColor;
import fr.vriege.anilib.feature.settings.LanguagePack;
import fr.vriege.anilib.feature.settings.NavigationStyle;
import fr.vriege.anilib.feature.settings.StartScreen;
import fr.vriege.anilib.feature.settings.ThemeFamily;
import fr.vriege.anilib.feature.settings.ThemeMode;
import fr.vriege.anilib.feature.settings.TypographyScale;
import fr.vriege.anilib.feature.settings.UnusedDataCleanupResult;

import java.util.function.Consumer;

public interface SettingsPresentation {
    SettingsSnapshot snapshot();

    AutoCloseable observe(Consumer<SettingsSnapshot> observer);

    void setLanguagePack(LanguagePack languagePack);

    void setThemeMode(ThemeMode themeMode);

    void setThemeFamily(ThemeFamily themeFamily);

    void setAccentColor(AccentColor accentColor);

    void setTypographyScale(TypographyScale typographyScale);

    void setNavigationStyle(NavigationStyle navigationStyle);

    void setStartScreen(StartScreen startScreen);

    void setShowAdultContent(boolean enabled);

    void setIncognitoMode(boolean enabled);

    void setDownloadOnlyOnWifi(boolean enabled);

    void setUpdateOnlyOnWifi(boolean enabled);

    UnusedDataCleanupResult cleanUnusedData();
}
