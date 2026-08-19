package fr.vriege.anilib.feature.applicationupdate.ui;

import fr.vriege.anilib.framework.localization.TranslationCatalog;

import java.util.Map;

public final class ApplicationUpdateTranslationCatalog {
    private ApplicationUpdateTranslationCatalog() {
    }

    public static TranslationCatalog catalog() {
        return TranslationCatalog.french("feature.application-update", Map.ofEntries(
                Map.entry("Application", "Application"),
                Map.entry("Check for updates", "Rechercher des mises à jour"),
                Map.entry("Changelog", "Journal des modifications"),
                Map.entry("Open release", "Ouvrir la version"),
                Map.entry("Update channel", "Canal de mise à jour"),
                Map.entry("Version, updates, project, and help", "Version, mises à jour, projet et aide"),
                Map.entry("Version", "Version")
        ));
    }
}
