package fr.vriege.anilib.feature.updates.ui;

import fr.vriege.anilib.framework.localization.TranslationCatalog;

import java.util.Map;

public final class UpdatesTranslationCatalog {
    private UpdatesTranslationCatalog() {
    }

    public static TranslationCatalog catalog() {
        return TranslationCatalog.french("feature.updates", Map.ofEntries(
                Map.entry("Always include", "Toujours inclure"),
                Map.entry("Cancel update", "Annuler la mise à jour"),
                Map.entry("Collapse", "Réduire"),
                Map.entry("Expand", "Développer"),
                Map.entry("Exclude titles", "Exclure des titres"),
                Map.entry("Library update schedule", "Planification des mises à jour de la bibliothèque"),
                Map.entry("Library updates", "Mises à jour de la bibliothèque"),
                Map.entry("Mark all read", "Tout marquer comme lu"),
                Map.entry("Run now", "Exécuter maintenant"),
                Map.entry("Schedule", "Planification"),
                Map.entry("Select", "Sélectionner"),
                Map.entry("Unread", "Non lus"),
                Map.entry("Update", "Mettre à jour"),
                Map.entry("Updating library", "Mise à jour de la bibliothèque"),
                Map.entry("Updates", "Mises à jour")
        ));
    }
}
