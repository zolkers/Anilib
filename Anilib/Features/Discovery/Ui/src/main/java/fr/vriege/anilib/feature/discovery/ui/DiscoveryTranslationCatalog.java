package fr.vriege.anilib.feature.discovery.ui;

import fr.vriege.anilib.framework.localization.TranslationCatalog;

import java.util.Map;

public final class DiscoveryTranslationCatalog {
    private DiscoveryTranslationCatalog() {
    }

    public static TranslationCatalog catalog() {
        return TranslationCatalog.french("feature.discovery", Map.ofEntries(
                Map.entry("Batch migration", "Migration par lot"),
                Map.entry("Browse", "Parcourir"),
                Map.entry("Change source", "Changer de source"),
                Map.entry("Choose a target source", "Choisir une source cible"),
                Map.entry("Explore", "Explorer"),
                Map.entry("Filters", "Filtres"),
                Map.entry("Global search", "Recherche globale"),
                Map.entry("Latest", "Derniers ajouts"),
                Map.entry("Migrate", "Migrer"),
                Map.entry("Popular", "Populaires"),
                Map.entry("Quick filters", "Filtres rapides"),
                Map.entry("Search all sources", "Rechercher dans toutes les sources"),
                Map.entry("Source actions", "Actions de la source")
        ));
    }
}
