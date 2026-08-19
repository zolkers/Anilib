package fr.vriege.anilib.feature.backup.ui;

import fr.vriege.anilib.feature.backup.AniyomiBackupInspection;
import fr.vriege.anilib.feature.backup.BackupInspection;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

public record BackupImportPreview(
        Path path,
        BackupImportFormat format,
        Optional<BackupInspection> anilib,
        Optional<AniyomiBackupInspection> aniyomi) {
    public BackupImportPreview {
        path = Objects.requireNonNull(path, "path").toAbsolutePath().normalize();
        format = Objects.requireNonNull(format, "format");
        anilib = Objects.requireNonNull(anilib, "anilib");
        aniyomi = Objects.requireNonNull(aniyomi, "aniyomi");
        if ((format == BackupImportFormat.ANILIB) != anilib.isPresent()
                || (format == BackupImportFormat.ANIYOMI) != aniyomi.isPresent()) {
            throw new IllegalArgumentException("backup preview must contain exactly its detected format");
        }
    }

    public static BackupImportPreview anilib(BackupInspection inspection) {
        BackupInspection value = Objects.requireNonNull(inspection, "inspection");
        return new BackupImportPreview(
                value.path(), BackupImportFormat.ANILIB, Optional.of(value), Optional.empty());
    }

    public static BackupImportPreview aniyomi(AniyomiBackupInspection inspection) {
        AniyomiBackupInspection value = Objects.requireNonNull(inspection, "inspection");
        return new BackupImportPreview(
                value.path(), BackupImportFormat.ANIYOMI, Optional.empty(), Optional.of(value));
    }
}
