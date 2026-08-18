package fr.vriege.anilib.feature.backup;

import java.nio.file.Path;
import java.util.List;

public interface BackupService {
    Path backupDirectory();

    List<BackupFileSnapshot> backups();

    BackupFileSnapshot createBackup();

    BackupInspection inspect(Path path);

    BackupRestoreResult restore(Path path);

    void delete(Path path);

    AutoCloseable observe(Runnable listener);
}
