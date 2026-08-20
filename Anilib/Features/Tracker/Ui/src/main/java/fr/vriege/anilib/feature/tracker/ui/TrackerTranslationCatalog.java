package fr.vriege.anilib.feature.tracker.ui;

import fr.vriege.anilib.framework.localization.TranslationCatalog;

public final class TrackerTranslationCatalog {
    private TrackerTranslationCatalog() {
    }

    public static TranslationCatalog catalog() {
        return TranslationCatalog.resources(
                "feature.tracker",
                TrackerTranslationCatalog.class,
                "META-INF/anilib/i18n/tracker");
    }
}
