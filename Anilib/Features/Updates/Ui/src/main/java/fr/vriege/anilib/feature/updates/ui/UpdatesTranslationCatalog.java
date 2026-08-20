package fr.vriege.anilib.feature.updates.ui;

import fr.vriege.anilib.framework.localization.TranslationCatalog;

public final class UpdatesTranslationCatalog {
    private UpdatesTranslationCatalog() {
    }

    public static TranslationCatalog catalog() {
        return TranslationCatalog.resources(
                "feature.updates",
                UpdatesTranslationCatalog.class,
                "META-INF/anilib/i18n/updates");
    }
}
