package fr.vriege.anilib.feature.player.ui;

import fr.vriege.anilib.framework.localization.TranslationCatalog;

public final class PlayerTranslationCatalog {
    private PlayerTranslationCatalog() {
    }

    public static TranslationCatalog catalog() {
        return TranslationCatalog.resources(
                "feature.player",
                PlayerTranslationCatalog.class,
                "META-INF/anilib/i18n/player");
    }
}
