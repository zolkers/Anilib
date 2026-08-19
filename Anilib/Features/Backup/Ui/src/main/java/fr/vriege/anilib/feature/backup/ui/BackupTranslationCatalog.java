package fr.vriege.anilib.feature.backup.ui;

import fr.vriege.anilib.framework.localization.TranslationCatalog;

import java.util.Map;

public final class BackupTranslationCatalog {
    private BackupTranslationCatalog() {
    }

    public static TranslationCatalog catalog() {
        return TranslationCatalog.french("feature.backup", Map.ofEntries(
                Map.entry("Automatic backup folder", "Dossier de sauvegarde automatique"),
                Map.entry("Backup", "Sauvegarde"),
                Map.entry("Backup and restore", "Sauvegarde et restauration"),
                Map.entry("Backup policy", "Politique de sauvegarde"),
                Map.entry("Create backup", "Créer une sauvegarde"),
                Map.entry("Delete backup", "Supprimer la sauvegarde"),
                Map.entry("Delete backup?", "Supprimer la sauvegarde ?"),
                Map.entry("Export", "Exporter"),
                Map.entry("Import", "Importer"),
                Map.entry("Import Aniyomi backup", "Importer une sauvegarde Aniyomi"),
                Map.entry("Protect or import your library", "Protéger ou importer votre bibliothèque"),
                Map.entry("Restore", "Restaurer"),
                Map.entry("Restore backup?", "Restaurer la sauvegarde ?")
        ));
    }
}
