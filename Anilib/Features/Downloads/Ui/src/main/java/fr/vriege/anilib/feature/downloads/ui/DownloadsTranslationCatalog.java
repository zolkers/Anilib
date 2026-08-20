package fr.vriege.anilib.feature.downloads.ui;

import fr.vriege.anilib.framework.localization.TranslationCatalog;

import java.util.Map;

public final class DownloadsTranslationCatalog {
    private DownloadsTranslationCatalog() {
    }

    public static TranslationCatalog catalog() {
        return TranslationCatalog.french("feature.downloads", Map.ofEntries(
                Map.entry("Automatic downloads", "Téléchargements automatiques"),
                Map.entry("Automatic", "Automatique"),
                Map.entry("Delete all", "Tout supprimer"),
                Map.entry("Delete all downloads?", "Supprimer tous les téléchargements ?"),
                Map.entry("Delete title downloads", "Supprimer les téléchargements du titre"),
                Map.entry("Download", "Télécharger"),
                Map.entry("Download queue", "File de téléchargement"),
                Map.entry("Download storage", "Stockage des téléchargements"),
                Map.entry("Downloaded only", "Téléchargés uniquement"),
                Map.entry("Downloads", "Téléchargements"),
                Map.entry("Hand downloads to the platform", "Confier les téléchargements à la plateforme"),
                Map.entry("Keep queued downloads off metered connections",
                        "Suspendre les téléchargements en attente sur les connexions limitées"),
                Map.entry("Manage downloads", "Gérer les téléchargements"),
                Map.entry("Move down", "Descendre"),
                Map.entry("Move up", "Monter"),
                Map.entry("Network policy and download queue", "Règles réseau et file de téléchargement"),
                Map.entry("No pending downloads", "Aucun téléchargement en attente"),
                Map.entry("Offline mode", "Mode hors ligne"),
                Map.entry("Pause all", "Tout mettre en pause"),
                Map.entry("Pause title", "Mettre le titre en pause"),
                Map.entry("Queue, offline mode, and storage usage", "File d’attente, mode hors ligne et stockage"),
                Map.entry("Resume all", "Tout reprendre"),
                Map.entry("Resume title", "Reprendre le titre"),
                Map.entry("Storage", "Stockage"),
                Map.entry("Use downloaded content without the online fallback",
                        "Utiliser le contenu téléchargé sans repli en ligne")
        ));
    }
}
