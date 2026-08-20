package fr.vriege.anilib.feature.reader.ui;

import fr.vriege.anilib.framework.localization.TranslationCatalog;

public final class ReaderTranslationCatalog {
    private ReaderTranslationCatalog() {
    }

    public static TranslationCatalog catalog() {
        return TranslationCatalog.resources(
                "feature.reader",
                ReaderTranslationCatalog.class,
                "META-INF/anilib/i18n/reader");
    }
}
