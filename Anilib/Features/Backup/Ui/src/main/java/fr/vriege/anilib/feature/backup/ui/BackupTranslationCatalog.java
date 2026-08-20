package fr.vriege.anilib.feature.backup.ui;

import fr.vriege.anilib.framework.localization.TranslationCatalog;

public final class BackupTranslationCatalog {
    private BackupTranslationCatalog() {
    }

    public static TranslationCatalog catalog() {
        return TranslationCatalog.resources(
                "feature.backup",
                BackupTranslationCatalog.class,
                "META-INF/anilib/i18n/backup");
    }
}
