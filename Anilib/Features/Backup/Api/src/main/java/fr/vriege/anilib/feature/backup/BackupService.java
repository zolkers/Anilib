package fr.vriege.anilib.feature.backup;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

public interface BackupService {
    Path backupDirectory();

    List<BackupFileSnapshot> backups();

    BackupFileSnapshot createBackup();

    List<BackupContentOption> contentOptions();

    BackupPolicy policy();

    void savePolicy(BackupPolicy policy);

    Optional<BackupFileSnapshot> runAutomaticBackupIfDue();

    Path export(Path backup, Path destinationDirectory);

    BackupInspection inspect(Path path);

    AniyomiBackupInspection inspectAniyomi(Path path);

    BackupRestoreResult restore(Path path);

    AniyomiBackupImportResult importAniyomi(Path path);

    void delete(Path path);

    AutoCloseable observe(Runnable listener);
}
