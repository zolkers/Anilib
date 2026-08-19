package fr.vriege.anilib.feature.settings.ui;

import fr.vriege.anilib.framework.localization.TranslationCatalog;

import java.util.Map;

public final class SettingsTranslationCatalog {
    private SettingsTranslationCatalog() {
    }

    public static TranslationCatalog catalog() {
        return TranslationCatalog.french("feature.settings", Map.ofEntries(
                Map.entry("About", "À propos"),
                Map.entry("Accent color", "Couleur d’accentuation"),
                Map.entry("Advanced", "Avancé"),
                Map.entry("Adult content and incognito mode", "Contenu adulte et mode navigation privée"),
                Map.entry("Allow sources and titles marked as adult", "Autoriser les sources et titres adultes"),
                Map.entry("Allow user-initiated file selection",
                        "Autoriser la sélection de fichiers par l’utilisateur"),
                Map.entry("Allow website local storage", "Autoriser le stockage local des sites"),
                Map.entry("Allow website scripts", "Autoriser les scripts des sites"),
                Map.entry("Always display Anilib in English", "Toujours afficher Anilib en anglais"),
                Map.entry("Appearance", "Apparence"),
                Map.entry("Browser settings", "Paramètres du navigateur"),
                Map.entry("Choose language", "Choisir la langue"),
                Map.entry("Content and privacy", "Contenu et confidentialité"),
                Map.entry("Data and storage", "Données et stockage"),
                Map.entry("Dark", "Sombre"),
                Map.entry("Disable reader page transitions and nonessential animation",
                        "Désactiver les transitions du lecteur et les animations non essentielles"),
                Map.entry("Do not write new reader or player history and progress",
                        "Ne pas enregistrer l’historique ni la progression de lecture et de visionnage"),
                Map.entry("English", "Anglais"),
                Map.entry("Follow the system theme", "Suivre le thème du système"),
                Map.entry("French", "Français"),
                Map.entry("General", "Général"),
                Map.entry("Language", "Langue"),
                Map.entry("Language and start screen", "Langue et écran de démarrage"),
                Map.entry("Light", "Clair"),
                Map.entry("JavaScript, storage, files, pop-ups, downloads, challenge retry, and text zoom",
                        "JavaScript, stockage, fichiers, fenêtres, téléchargements, nouvelle tentative et zoom"),
                Map.entry("Inspect storage, logs, crash reports, export, and safe reset",
                        "Examiner le stockage, les journaux, les rapports, l’exportation et la réinitialisation sûre"),
                Map.entry("Navigation", "Navigation"),
                Map.entry("Network, browser, and unused data", "Réseau, navigateur et données inutilisées"),
                Map.entry("Network and browser", "Réseau et navigateur"),
                Map.entry("Open requested windows in the current browser",
                        "Ouvrir les fenêtres demandées dans le navigateur actuel"),
                Map.entry("Read and write shared HTTP cache entries", "Lire et écrire le cache HTTP partagé"),
                Map.entry("Reduce motion", "Réduire les animations"),
                Map.entry("Refresh and content policies", "Actualisation et règles de contenu"),
                Map.entry("Refresh the library automatically only on Wi-Fi",
                        "Actualiser automatiquement la bibliothèque uniquement en Wi-Fi"),
                Map.entry("Remove browser cookies, cache, and site storage",
                        "Supprimer les cookies, le cache et le stockage des sites"),
                Map.entry("Remove cached HTTP responses", "Supprimer les réponses HTTP en cache"),
                Map.entry("Remove records for titles no longer in the library",
                        "Supprimer les données des titres qui ne sont plus dans la bibliothèque"),
                Map.entry("Search settings", "Rechercher dans les paramètres"),
                Map.entry("Settings", "Paramètres"),
                Map.entry("Sign out browser sessions for every source",
                        "Déconnecter les sessions web de chaque source"),
                Map.entry("Start screen", "Écran de démarrage"),
                Map.entry("System language", "Langue du système"),
                Map.entry("Theme", "Thème"),
                Map.entry("Theme and visual preferences", "Thème et préférences visuelles"),
                Map.entry("Theme family", "Famille de thèmes"),
                Map.entry("Typography", "Typographie"),
                Map.entry("User agent, proxy, DNS-over-HTTPS, timeout, cache, and diagnostics",
                        "Agent utilisateur, proxy, DNS-over-HTTPS, délai, cache et diagnostics"),
                Map.entry("Use the device language", "Utiliser la langue de l’appareil")
        ));
    }
}
