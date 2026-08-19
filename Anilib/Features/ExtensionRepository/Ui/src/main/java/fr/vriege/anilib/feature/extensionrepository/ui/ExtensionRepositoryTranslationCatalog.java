package fr.vriege.anilib.feature.extensionrepository.ui;

import fr.vriege.anilib.framework.localization.TranslationCatalog;

import java.util.Map;

public final class ExtensionRepositoryTranslationCatalog {
    private ExtensionRepositoryTranslationCatalog() {
    }

    public static TranslationCatalog catalog() {
        return TranslationCatalog.french("feature.extension-repository", Map.ofEntries(
                Map.entry("18+ · enable adult content in Settings to install",
                        "18+ · activez le contenu adulte dans les paramètres pour installer"),
                Map.entry("Add repository", "Ajouter un dépôt"),
                Map.entry("Adult-content policy", "Politique de contenu adulte"),
                Map.entry("Automatic source updates", "Mises à jour automatiques des sources"),
                Map.entry("Available in Sources", "Disponible dans les sources"),
                Map.entry("Browse extensions", "Parcourir les extensions"),
                Map.entry("Extension actions", "Actions de l’extension"),
                Map.entry("Extension languages", "Langues des extensions"),
                Map.entry("Extension repositories", "Dépôts d’extensions"),
                Map.entry("Extensions", "Extensions"),
                Map.entry("Install", "Installer"),
                Map.entry("Installed", "Installé"),
                Map.entry("Installed Anilib Bundles", "Bundles Anilib installés"),
                Map.entry("Installed APK extensions", "Extensions APK installées"),
                Map.entry("Installed desktop extensions", "Extensions desktop installées"),
                Map.entry("Installed extensions", "Extensions installées"),
                Map.entry("Installed · available in Sources", "Installée · disponible dans les sources"),
                Map.entry("Installed · no compatible source activated",
                        "Installée · aucune source compatible activée"),
                Map.entry("Manage repositories", "Gérer les dépôts"),
                Map.entry("No extensions installed", "Aucune extension installée"),
                Map.entry("No installed extension matches your search.",
                        "Aucune extension installée ne correspond à votre recherche."),
                Map.entry("No compatible source activated", "Aucune source compatible activée"),
                Map.entry("Refresh repositories", "Actualiser les dépôts"),
                Map.entry("Remove repository?", "Supprimer le dépôt ?"),
                Map.entry("Retry the source as soon as all completion cookies exist",
                        "Réessayer la source dès que tous les cookies de validation sont présents"),
                Map.entry("Search anime extensions", "Rechercher des extensions d’anime"),
                Map.entry("Search installed extensions", "Rechercher dans les extensions installées"),
                Map.entry("Search manga extensions", "Rechercher des extensions de manga"),
                Map.entry("This extension remains listed, but installation requires enabling adult content "
                                + "in Settings.",
                        "Cette extension reste affichée, mais son installation nécessite d’activer le contenu adulte "
                                + "dans les paramètres."),
                Map.entry("The repository will disappear from Anilib. Installed extensions are kept.",
                        "Le dépôt disparaîtra d’Anilib. Les extensions déjà installées seront conservées."),
                Map.entry("Listed · enable adult content in Settings to install",
                        "Affichée · activez le contenu adulte dans les paramètres pour installer"),
                Map.entry("Uninstall", "Désinstaller"),
                Map.entry("Uninstall extension?", "Désinstaller l’extension ?")
        ));
    }
}
