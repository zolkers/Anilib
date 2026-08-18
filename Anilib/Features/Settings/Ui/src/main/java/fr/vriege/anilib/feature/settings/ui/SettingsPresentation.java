package fr.vriege.anilib.feature.settings.ui;

import fr.vriege.anilib.feature.settings.DiagnosticResetArea;
import fr.vriege.anilib.feature.settings.DiagnosticResetPlan;
import fr.vriege.anilib.feature.settings.DiagnosticSnapshot;
import fr.vriege.anilib.feature.settings.BrowserPolicy;
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
import java.nio.file.Path;
import java.util.Set;

public interface SettingsPresentation {
    SettingsSnapshot snapshot();

    AutoCloseable observe(Consumer<SettingsSnapshot> observer);

    void setLanguagePack(LanguagePack languagePack);

    void setThemeMode(ThemeMode themeMode);

    void setThemeFamily(ThemeFamily themeFamily);

    void setAccentColor(AccentColor accentColor);

    void setTypographyScale(TypographyScale typographyScale);

    void setNavigationStyle(NavigationStyle navigationStyle);

    void setBrowserPolicy(BrowserPolicy browserPolicy);

    void setStartScreen(StartScreen startScreen);

    void setShowAdultContent(boolean enabled);

    void setIncognitoMode(boolean enabled);

    void setDownloadOnlyOnWifi(boolean enabled);

    void setUpdateOnlyOnWifi(boolean enabled);

    UnusedDataCleanupResult cleanUnusedData();

    DiagnosticSnapshot diagnostics();

    Path exportDiagnostics();

    DiagnosticResetPlan planReset(Set<DiagnosticResetArea> areas);

    void executeReset(DiagnosticResetPlan plan);
}
