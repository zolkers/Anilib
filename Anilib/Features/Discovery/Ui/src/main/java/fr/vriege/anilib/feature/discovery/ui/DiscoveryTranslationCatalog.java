package fr.vriege.anilib.feature.discovery.ui;

import fr.vriege.anilib.framework.localization.TranslationCatalog;

public final class DiscoveryTranslationCatalog {
    private DiscoveryTranslationCatalog() {
    }

    public static TranslationCatalog catalog() {
        return TranslationCatalog.resources(
                "feature.discovery",
                DiscoveryTranslationCatalog.class,
                "META-INF/anilib/i18n/discovery");
    }
}
