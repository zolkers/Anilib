package fr.vriege.anilib.feature.backup.ui;

import fr.vriege.anilib.feature.backup.BackupFileSnapshot;
import fr.vriege.anilib.feature.backup.BackupInspection;
import fr.vriege.anilib.feature.backup.BackupRestoreResult;

import java.nio.file.Path;
import java.util.List;

/** Platform-neutral backup screen commands shared by Android and desktop. */
public interface BackupPresentation {
    Path backupDirectory();

    List<BackupFileSnapshot> backups();

    BackupFileSnapshot createBackup();

    BackupInspection inspect(Path path);

    BackupRestoreResult restore(Path path);

    void delete(Path path);

    AutoCloseable observe(Runnable listener);
}
