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
                Map.entry("Comfortable", "Confortable"),
                Map.entry("Compact", "Compact"),
                Map.entry("Create", "Créer"),
                Map.entry("Create category", "Créer une catégorie"),
                Map.entry("Density", "Densité"),
                Map.entry("Display mode", "Mode d’affichage"),
                Map.entry("Each category can use its own layout, density, sort and update policy.",
                        "Chaque catégorie peut utiliser sa propre disposition, densité, "
                                + "ordre et politique de mise à jour."),
                Map.entry("Edit category", "Modifier la catégorie"),
                Map.entry("Exclude", "Exclure"),
                Map.entry("Favorite", "Ajouter aux favoris"),
                Map.entry("Favorites", "Favoris"),
                Map.entry("Grid", "Grille"),
                Map.entry("History", "Historique"),
                Map.entry("In Library", "Dans la bibliothèque"),
                Map.entry("Library", "Bibliothèque"),
                Map.entry("Library updates", "Mises à jour de la bibliothèque"),
                Map.entry("List", "Liste"),
                Map.entry("New category", "Nouvelle catégorie"),
                Map.entry("Oldest added", "Ajouts les plus anciens"),
                Map.entry("Organize your library", "Organiser votre bibliothèque"),
                Map.entry("Recently added", "Ajouts récents"),
                Map.entry("Relaxed", "Aéré"),
                Map.entry("Search history", "Rechercher dans l’historique"),
                Map.entry("Search library", "Rechercher dans la bibliothèque"),
                Map.entry("Sort", "Tri"),
                Map.entry("Sort library", "Trier la bibliothèque"),
                Map.entry("Title A–Z", "Titre A–Z"),
                Map.entry("Title Z–A", "Titre Z–A"),
                Map.entry("Unfavorite", "Retirer des favoris"),
                Map.entry("Unwatched", "Non vus"),
                Map.entry("Use global policy", "Utiliser la politique globale")
        ));
    }
}
