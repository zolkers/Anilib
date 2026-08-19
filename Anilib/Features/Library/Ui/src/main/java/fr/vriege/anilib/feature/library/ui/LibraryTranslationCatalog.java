package fr.vriege.anilib.feature.library.ui;

import fr.vriege.anilib.framework.localization.TranslationCatalog;

import java.util.Map;

public final class LibraryTranslationCatalog {
    private LibraryTranslationCatalog() {
    }

    public static TranslationCatalog catalog() {
        return TranslationCatalog.french("feature.library", Map.ofEntries(
                Map.entry("Add to Library", "Ajouter à la bibliothèque"),
                Map.entry("All categories", "Toutes les catégories"),
                Map.entry("Anime", "Animé"),
                Map.entry("Categories", "Catégories"),
                Map.entry("Category", "Catégorie"),
                Map.entry("Favorite", "Ajouter aux favoris"),
                Map.entry("Favorites", "Favoris"),
                Map.entry("History", "Historique"),
                Map.entry("In Library", "Dans la bibliothèque"),
                Map.entry("Library", "Bibliothèque"),
                Map.entry("New category", "Nouvelle catégorie"),
                Map.entry("Search history", "Rechercher dans l’historique"),
                Map.entry("Search library", "Rechercher dans la bibliothèque"),
                Map.entry("Sort library", "Trier la bibliothèque"),
                Map.entry("Unfavorite", "Retirer des favoris"),
                Map.entry("Unwatched", "Non vus")
        ));
    }
}
