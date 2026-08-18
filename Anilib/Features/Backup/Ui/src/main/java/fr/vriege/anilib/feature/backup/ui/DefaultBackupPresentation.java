package fr.vriege.anilib.feature.backup.ui;

import fr.vriege.anilib.feature.backup.BackupFileSnapshot;
import fr.vriege.anilib.feature.backup.BackupInspection;
import fr.vriege.anilib.feature.backup.BackupRestoreResult;
import fr.vriege.anilib.feature.backup.BackupService;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/** Thin presentation adapter retaining all backup policy in the Java service. */
public final class DefaultBackupPresentation implements BackupPresentation {
    private final BackupService service;

    public DefaultBackupPresentation(BackupService service) {
        this.service = Objects.requireNonNull(service, "service must not be null");
    }

    @Override
    public Path backupDirectory() {
        return service.backupDirectory();
    }

    @Override
    public List<BackupFileSnapshot> backups() {
        return service.backups();
    }

    @Override
    public BackupFileSnapshot createBackup() {
        return service.createBackup();
    }

    @Override
    public BackupInspection inspect(Path path) {
        return service.inspect(path);
    }

    @Override
    public BackupRestoreResult restore(Path path) {
        return service.restore(path);
    }

    @Override
    public void delete(Path path) {
        service.delete(path);
    }

    @Override
    public AutoCloseable observe(Runnable listener) {
        return service.observe(listener);
    }
}
