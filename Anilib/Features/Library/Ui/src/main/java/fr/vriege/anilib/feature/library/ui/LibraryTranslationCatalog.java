package fr.vriege.anilib.feature.library.ui;

import fr.vriege.anilib.framework.localization.TranslationCatalog;

public final class LibraryTranslationCatalog {
    private LibraryTranslationCatalog() {
    }

    public static TranslationCatalog catalog() {
        return TranslationCatalog.resources(
                "feature.library",
                LibraryTranslationCatalog.class,
                "META-INF/anilib/i18n/library");
    }
}
