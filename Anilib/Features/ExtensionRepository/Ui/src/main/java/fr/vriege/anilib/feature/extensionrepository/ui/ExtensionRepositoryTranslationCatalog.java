package fr.vriege.anilib.feature.extensionrepository.ui;

import fr.vriege.anilib.framework.localization.TranslationCatalog;

public final class ExtensionRepositoryTranslationCatalog {
    private ExtensionRepositoryTranslationCatalog() {
    }

    public static TranslationCatalog catalog() {
        return TranslationCatalog.resources(
                "feature.extension-repository",
                ExtensionRepositoryTranslationCatalog.class,
                "META-INF/anilib/i18n/extension-repository");
    }
}
