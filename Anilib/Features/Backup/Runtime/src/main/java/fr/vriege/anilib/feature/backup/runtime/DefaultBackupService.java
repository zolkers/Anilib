package fr.vriege.anilib.feature.backup.runtime;

import fr.vriege.anilib.feature.backup.BackupException;
import fr.vriege.anilib.feature.backup.BackupFileSnapshot;
import fr.vriege.anilib.feature.backup.BackupInspection;
import fr.vriege.anilib.feature.backup.BackupRestoreResult;
import fr.vriege.anilib.feature.backup.BackupSectionSnapshot;
import fr.vriege.anilib.feature.backup.BackupService;
import fr.vriege.anilib.feature.backup.AniyomiBackupImportResult;
import fr.vriege.anilib.feature.backup.AniyomiBackupInspection;
import fr.vriege.anilib.feature.library.LibraryCatalog;
import fr.vriege.anilib.framework.backup.BackupSectionCodec;
import fr.vriege.anilib.framework.backup.BackupSectionData;
import fr.vriege.anilib.framework.backup.BackupSectionDetails;
import fr.vriege.anilib.framework.backup.BackupSectionId;
import fr.vriege.anilib.framework.backup.PreparedBackupRestore;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class DefaultBackupService implements BackupService, AutoCloseable {
    private static final DateTimeFormatter FILE_TIME =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC);
    private final Path backupDirectory;
    private final Map<BackupSectionId, BackupSectionCodec> codecs;
    private final Clock clock;
    private final AniyomiBackupImporter aniyomiImporter;
    private final BackupArchiveStore store = new BackupArchiveStore();
    private final Set<Runnable> listeners = new HashSet<>();
    private boolean closed;

    public DefaultBackupService(Path backupDirectory, List<BackupSectionCodec> codecs) {
        this(backupDirectory, codecs, null, Clock.systemUTC());
    }

    public DefaultBackupService(
            Path backupDirectory,
            List<BackupSectionCodec> codecs,
            Clock clock) {
        this(backupDirectory, codecs, null, clock);
    }

    public DefaultBackupService(
            Path backupDirectory,
            List<BackupSectionCodec> codecs,
            LibraryCatalog library) {
        this(backupDirectory, codecs, library, Clock.systemUTC());
    }

    public DefaultBackupService(
            Path backupDirectory,
            List<BackupSectionCodec> codecs,
            LibraryCatalog library,
            Clock clock) {
        this.backupDirectory = Objects.requireNonNull(
                backupDirectory,
                "backupDirectory must not be null").toAbsolutePath().normalize();
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.aniyomiImporter = library == null ? null : new AniyomiBackupImporter(library, clock);
        Map<BackupSectionId, BackupSectionCodec> indexed = new LinkedHashMap<>();
        Objects.requireNonNull(codecs, "codecs must not be null").stream()
                .sorted(Comparator.comparing(BackupSectionCodec::sectionId))
                .forEach(codec -> {
                    Objects.requireNonNull(codec, "codecs must not contain null");
                    if (codec.currentVersion() < 0) {
                        throw new IllegalArgumentException("codec version must not be negative");
                    }
                    if (indexed.putIfAbsent(codec.sectionId(), codec) != null) {
                        throw new IllegalArgumentException("duplicate backup codec: " + codec.sectionId());
                    }
                });
        if (indexed.isEmpty()) {
            throw new IllegalArgumentException("at least one backup codec is required");
        }
        this.codecs = Collections.unmodifiableMap(new LinkedHashMap<>(indexed));
    }

    @Override
    public Path backupDirectory() {
        return backupDirectory;
    }

    @Override
    public synchronized List<BackupFileSnapshot> backups() {
        ensureOpen();
        if (!Files.exists(backupDirectory)) {
            return List.of();
        }
        validateExistingDirectory();
        try (java.util.stream.Stream<Path> entries = Files.list(backupDirectory)) {
            return entries
                    .filter(this::isManagedBackup)
                    .map(path -> {
                        try {
                            return fileSnapshot(inspectInternal(path));
                        } catch (BackupException exception) {
                            return null;
                        }
                    })
                    .filter(Objects::nonNull)
                    .sorted(Comparator.comparing(BackupFileSnapshot::createdAt).reversed()
                            .thenComparing(snapshot -> snapshot.path().toString()))
                    .toList();
        } catch (IOException exception) {
            throw new BackupException("Unable to list local backups", exception);
        }
    }

    @Override
    public synchronized BackupFileSnapshot createBackup() {
        ensureOpen();
        Instant createdAt = clock.instant();
        List<BackupArchiveStore.EncodedSection> sections = codecs.values().stream()
                .map(codec -> encoded(codec, codec.exportSection()))
                .toList();
        Path temporary = null;
        try {
            Files.createDirectories(backupDirectory);
            if (!Files.isDirectory(backupDirectory) || Files.isSymbolicLink(backupDirectory)) {
                throw new BackupException("Backup directory must be a non-symbolic directory");
            }
            Path destination = availablePath(createdAt);
            temporary = Files.createTempFile(backupDirectory, ".anilib-backup-", ".tmp");
            store.write(temporary, new BackupArchiveStore.Archive(createdAt, sections));
            moveAtomically(temporary, destination);
            temporary = null;
            BackupFileSnapshot snapshot = fileSnapshot(inspectInternal(destination));
            notifyListeners();
            return snapshot;
        } catch (IOException exception) {
            throw new BackupException("Unable to create backup archive", exception);
        } finally {
            deleteTemporary(temporary);
        }
    }

    @Override
    public synchronized BackupInspection inspect(Path path) {
        ensureOpen();
        return inspectInternal(path);
    }

    @Override
    public synchronized AniyomiBackupInspection inspectAniyomi(Path path) {
        ensureOpen();
        return requireAniyomiImporter().inspect(path);
    }

    @Override
    public synchronized BackupRestoreResult restore(Path path) {
        ensureOpen();
        BackupArchiveStore.Archive archive = store.read(path);
        List<PreparedEntry> prepared = new ArrayList<>();
        RuntimeException restoreFailure = null;
        try {
            for (BackupArchiveStore.EncodedSection section : archive.sections()) {
                BackupSectionCodec codec = codecs.get(section.id());
                if (codec == null) {
                    continue;
                }
                BackupSectionSnapshot snapshot = inspectSection(section, codec);
                PreparedBackupRestore restore = codec.prepareRestore(section.version(), section.payload());
                prepared.add(new PreparedEntry(snapshot, restore));
            }
            if (prepared.isEmpty()) {
                throw new BackupException("Backup contains no sections restorable by this product");
            }
            commitAll(prepared);
            BackupRestoreResult result = new BackupRestoreResult(
                    clock.instant(),
                    prepared.stream().map(PreparedEntry::snapshot).toList());
            notifyListeners();
            return result;
        } catch (RuntimeException exception) {
            restoreFailure = exception;
            throw exception;
        } finally {
            RuntimeException closeFailure = closeAll(prepared);
            if (closeFailure != null) {
                if (restoreFailure != null) {
                    restoreFailure.addSuppressed(closeFailure);
                } else {
                    throw new BackupException("Unable to release prepared backup restore", closeFailure);
                }
            }
        }
    }

    @Override
    public synchronized AniyomiBackupImportResult importAniyomi(Path path) {
        ensureOpen();
        AniyomiBackupImportResult result = requireAniyomiImporter().importBackup(path);
        notifyListeners();
        return result;
    }

    @Override
    public synchronized void delete(Path path) {
        ensureOpen();
        Path target = managedTarget(path);
        try {
            if (!Files.isRegularFile(target) || Files.isSymbolicLink(target)) {
                throw new BackupException("Managed backup must be a regular non-symbolic file");
            }
            Files.delete(target);
            notifyListeners();
        } catch (IOException exception) {
            throw new BackupException("Unable to delete local backup", exception);
        }
    }

    @Override
    public synchronized AutoCloseable observe(Runnable listener) {
        ensureOpen();
        Runnable value = Objects.requireNonNull(listener, "listener must not be null");
        listeners.add(value);
        return () -> removeListener(value);
    }

    private BackupInspection inspectInternal(Path path) {
        Path normalized = Objects.requireNonNull(path, "path must not be null").toAbsolutePath().normalize();
        BackupArchiveStore.Archive archive = store.read(normalized);
        List<BackupSectionSnapshot> sections = archive.sections().stream()
                .map(section -> inspectSection(section, codecs.get(section.id())))
                .toList();
        try {
            return new BackupInspection(normalized, archive.createdAt(), Files.size(normalized), sections);
        } catch (IOException exception) {
            throw new BackupException("Unable to inspect backup size", exception);
        }
    }

    private static BackupSectionSnapshot inspectSection(
            BackupArchiveStore.EncodedSection section,
            BackupSectionCodec codec) {
        if (codec == null) {
            return new BackupSectionSnapshot(
                    section.id(),
                    section.id().value(),
                    section.version(),
                    section.entryCount(),
                    false);
        }
        BackupSectionDetails details = codec.inspect(section.version(), section.payload());
        if (!details.id().equals(section.id())
                || details.version() != section.version()
                || details.entryCount() != section.entryCount()) {
            throw new BackupException("Codec metadata does not match backup section " + section.id());
        }
        return new BackupSectionSnapshot(
                details.id(),
                details.displayName(),
                details.version(),
                details.entryCount(),
                true);
    }

    private static BackupArchiveStore.EncodedSection encoded(
            BackupSectionCodec codec,
            BackupSectionData data) {
        return new BackupArchiveStore.EncodedSection(
                codec.sectionId(),
                codec.currentVersion(),
                data.entryCount(),
                data.payload());
    }

    private static void commitAll(List<PreparedEntry> prepared) {
        List<PreparedEntry> committed = new ArrayList<>();
        try {
            for (PreparedEntry entry : prepared) {
                entry.restore().commit();
                committed.add(entry);
            }
        } catch (RuntimeException failure) {
            for (int index = committed.size() - 1; index >= 0; index--) {
                try {
                    committed.get(index).restore().rollback();
                } catch (RuntimeException rollbackFailure) {
                    failure.addSuppressed(rollbackFailure);
                }
            }
            throw new BackupException("Backup restore failed and committed sections were rolled back", failure);
        }
    }

    private static RuntimeException closeAll(List<PreparedEntry> prepared) {
        RuntimeException failure = null;
        for (int index = prepared.size() - 1; index >= 0; index--) {
            try {
                prepared.get(index).restore().close();
            } catch (RuntimeException exception) {
                if (failure == null) {
                    failure = exception;
                } else {
                    failure.addSuppressed(exception);
                }
            }
        }
        return failure;
    }

    private BackupFileSnapshot fileSnapshot(BackupInspection inspection) {
        return new BackupFileSnapshot(
                inspection.path(),
                inspection.createdAt(),
                inspection.sizeBytes(),
                inspection.sections().size(),
                inspection.entryCount());
    }

    private Path availablePath(Instant createdAt) {
        String stem = "anilib-backup-" + FILE_TIME.format(createdAt);
        for (int suffix = 0; suffix < 10_000; suffix++) {
            String name = stem + (suffix == 0 ? "" : "-" + suffix) + BackupArchiveStore.EXTENSION;
            Path candidate = backupDirectory.resolve(name);
            if (!Files.exists(candidate)) {
                return candidate;
            }
        }
        throw new BackupException("Unable to allocate a unique backup filename");
    }

    private boolean isManagedBackup(Path path) {
        Path normalized = path.toAbsolutePath().normalize();
        return normalized.getParent().equals(backupDirectory)
                && normalized.getFileName().toString().endsWith(BackupArchiveStore.EXTENSION)
                && Files.isRegularFile(normalized)
                && !Files.isSymbolicLink(normalized);
    }

    private Path managedTarget(Path path) {
        validateExistingDirectory();
        Path normalized = Objects.requireNonNull(path, "path must not be null").toAbsolutePath().normalize();
        if (!normalized.getParent().equals(backupDirectory)
                || !normalized.getFileName().toString().endsWith(BackupArchiveStore.EXTENSION)) {
            throw new BackupException("Only local files in the managed backup directory can be deleted");
        }
        return normalized;
    }

    private void validateExistingDirectory() {
        if (!Files.isDirectory(backupDirectory) || Files.isSymbolicLink(backupDirectory)) {
            throw new BackupException("Backup directory must be a non-symbolic directory");
        }
    }

    private static void moveAtomically(Path source, Path destination) throws IOException {
        try {
            Files.move(source, destination, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException exception) {
            throw new IOException("Atomic backup creation is unavailable", exception);
        }
    }

    private static void deleteTemporary(Path temporary) {
        if (temporary == null) {
            return;
        }
        try {
            Files.deleteIfExists(temporary);
        } catch (IOException ignored) {
            // The primary operation reports the actionable failure.
        }
    }

    private void notifyListeners() {
        List.copyOf(listeners).forEach(listener -> {
            try {
                listener.run();
            } catch (RuntimeException ignored) {
                // Observers cannot compromise backup durability.
            }
        });
    }

    private synchronized void removeListener(Runnable listener) {
        listeners.remove(listener);
    }

    private void ensureOpen() {
        if (closed) {
            throw new BackupException("Backup service is closed");
        }
    }

    private AniyomiBackupImporter requireAniyomiImporter() {
        if (aniyomiImporter == null) {
            throw new BackupException("Aniyomi import requires the Library capability");
        }
        return aniyomiImporter;
    }

    @Override
    public synchronized void close() {
        closed = true;
        listeners.clear();
    }

    private record PreparedEntry(
            BackupSectionSnapshot snapshot,
            PreparedBackupRestore restore) {
    }
}
