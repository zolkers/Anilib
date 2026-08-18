package fr.vriege.anilib.feature.settings.ui;

import fr.vriege.anilib.feature.settings.SettingsSnapshot;
import fr.vriege.anilib.feature.settings.ThemeMode;

import java.util.function.Consumer;

/** Platform-neutral settings state and user actions. */
public interface SettingsPresentation {
    SettingsSnapshot snapshot();

    AutoCloseable observe(Consumer<SettingsSnapshot> observer);

    void setThemeMode(ThemeMode themeMode);

    void setShowAdultContent(boolean enabled);

    void setIncognitoMode(boolean enabled);

    void setDownloadOnlyOnWifi(boolean enabled);

    void setUpdateOnlyOnWifi(boolean enabled);
}
