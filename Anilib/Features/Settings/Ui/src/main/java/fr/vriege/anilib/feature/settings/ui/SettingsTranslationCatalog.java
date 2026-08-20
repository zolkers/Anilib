package fr.vriege.anilib.feature.settings.ui;

import fr.vriege.anilib.framework.localization.TranslationCatalog;

public final class SettingsTranslationCatalog {
    private SettingsTranslationCatalog() {
    }

    public static TranslationCatalog catalog() {
        return TranslationCatalog.resources(
                "feature.settings",
                SettingsTranslationCatalog.class,
                "META-INF/anilib/i18n/settings");
    }
}
