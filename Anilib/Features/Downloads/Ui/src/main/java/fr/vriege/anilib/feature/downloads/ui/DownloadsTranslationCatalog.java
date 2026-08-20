package fr.vriege.anilib.feature.downloads.ui;

import fr.vriege.anilib.framework.localization.TranslationCatalog;

public final class DownloadsTranslationCatalog {
    private DownloadsTranslationCatalog() {
    }

    public static TranslationCatalog catalog() {
        return TranslationCatalog.resources(
                "feature.downloads",
                DownloadsTranslationCatalog.class,
                "META-INF/anilib/i18n/downloads");
    }
}
