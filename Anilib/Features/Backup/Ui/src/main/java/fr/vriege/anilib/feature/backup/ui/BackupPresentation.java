package fr.vriege.anilib.feature.backup.ui;

import fr.vriege.anilib.feature.backup.BackupFileSnapshot;
import fr.vriege.anilib.feature.backup.BackupInspection;
import fr.vriege.anilib.feature.backup.BackupRestoreResult;
import fr.vriege.anilib.feature.backup.AniyomiBackupImportResult;
import fr.vriege.anilib.feature.backup.AniyomiBackupInspection;

import java.nio.file.Path;
import java.util.List;

public interface BackupPresentation {
    Path backupDirectory();

    List<BackupFileSnapshot> backups();

    BackupFileSnapshot createBackup();

    BackupInspection inspect(Path path);

    AniyomiBackupInspection inspectAniyomi(Path path);

    BackupRestoreResult restore(Path path);

    AniyomiBackupImportResult importAniyomi(Path path);

    void delete(Path path);

    AutoCloseable observe(Runnable listener);
}
