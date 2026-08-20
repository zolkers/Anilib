package fr.vriege.anilib.feature.applicationupdate.ui;

import fr.vriege.anilib.framework.localization.TranslationCatalog;

public final class ApplicationUpdateTranslationCatalog {
    private ApplicationUpdateTranslationCatalog() {
    }

    public static TranslationCatalog catalog() {
        return TranslationCatalog.resources(
                "feature.application-update",
                ApplicationUpdateTranslationCatalog.class,
                "META-INF/anilib/i18n/application-update");
    }
}
