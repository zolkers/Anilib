package fr.vriege.anilib.feature.backup.ui;

import fr.vriege.anilib.feature.backup.BackupFileSnapshot;
import fr.vriege.anilib.feature.backup.BackupInspection;
import fr.vriege.anilib.feature.backup.BackupContentOption;
import fr.vriege.anilib.feature.backup.BackupPolicy;
import fr.vriege.anilib.feature.backup.BackupRestoreResult;
import fr.vriege.anilib.feature.backup.AniyomiBackupImportResult;
import fr.vriege.anilib.feature.backup.AniyomiBackupInspection;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

public interface BackupPresentation {
    Path backupDirectory();

    List<BackupFileSnapshot> backups();

    BackupFileSnapshot createBackup();

    List<BackupContentOption> contentOptions();

    BackupPolicy policy();

    void savePolicy(BackupPolicy policy);

    Optional<BackupFileSnapshot> runAutomaticBackupIfDue();

    Path export(Path backup, Path destinationDirectory);

    BackupInspection inspect(Path path);

    BackupImportPreview inspectImport(Path path);

    AniyomiBackupInspection inspectAniyomi(Path path);

    BackupRestoreResult restore(Path path);

    AniyomiBackupImportResult importAniyomi(Path path);

    void delete(Path path);

    AutoCloseable observe(Runnable listener);
}
