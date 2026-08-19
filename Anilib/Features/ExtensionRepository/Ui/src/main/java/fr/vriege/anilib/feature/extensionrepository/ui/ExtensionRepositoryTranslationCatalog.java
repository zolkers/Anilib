package fr.vriege.anilib.feature.extensionrepository.ui;

import fr.vriege.anilib.framework.localization.TranslationCatalog;

import java.util.Map;

public final class ExtensionRepositoryTranslationCatalog {
    private ExtensionRepositoryTranslationCatalog() {
    }

    public static TranslationCatalog catalog() {
        return TranslationCatalog.french("feature.extension-repository", Map.ofEntries(
                Map.entry("Add repository", "Ajouter un dépôt"),
                Map.entry("Automatic source updates", "Mises à jour automatiques des sources"),
                Map.entry("Browse extensions", "Parcourir les extensions"),
                Map.entry("Extension actions", "Actions de l’extension"),
                Map.entry("Extension languages", "Langues des extensions"),
                Map.entry("Extension repositories", "Dépôts d’extensions"),
                Map.entry("Extensions", "Extensions"),
                Map.entry("Install", "Installer"),
                Map.entry("Installed", "Installé"),
                Map.entry("Manage repositories", "Gérer les dépôts"),
                Map.entry("No extensions installed", "Aucune extension installée"),
                Map.entry("Refresh repositories", "Actualiser les dépôts"),
                Map.entry("Retry the source as soon as all completion cookies exist",
                        "Réessayer la source dès que tous les cookies de validation sont présents"),
                Map.entry("Search anime extensions", "Rechercher des extensions d’anime"),
                Map.entry("Search manga extensions", "Rechercher des extensions de manga")
        ));
    }
}
