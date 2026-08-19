package fr.vriege.anilib.feature.tracker.ui;

import fr.vriege.anilib.framework.localization.TranslationCatalog;

import java.util.Map;

public final class TrackerTranslationCatalog {
    private TrackerTranslationCatalog() {
    }

    public static TranslationCatalog catalog() {
        return TranslationCatalog.french("feature.tracker", Map.ofEntries(
                Map.entry("Add tracking", "Ajouter un suivi"),
                Map.entry("Automatic synchronization", "Synchronisation automatique"),
                Map.entry("Connected", "Connecté"),
                Map.entry("Disconnect", "Déconnecter"),
                Map.entry("Link AniList, Kitsu, and synchronize progress",
                        "Associer AniList et Kitsu, puis synchroniser la progression"),
                Map.entry("Manage external tracking accounts", "Gérer les comptes de suivi externes"),
                Map.entry("Private entry", "Entrée privée"),
                Map.entry("Services", "Services"),
                Map.entry("Sign in", "Se connecter"),
                Map.entry("Sign out", "Se déconnecter"),
                Map.entry("Sync", "Synchroniser"),
                Map.entry("Synchronize now", "Synchroniser maintenant"),
                Map.entry("Tracking", "Suivi")
        ));
    }
}
