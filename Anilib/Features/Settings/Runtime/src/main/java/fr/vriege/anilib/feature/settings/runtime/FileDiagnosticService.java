package fr.vriege.anilib.feature.settings.runtime;

import fr.vriege.anilib.feature.settings.DiagnosticReport;
import fr.vriege.anilib.feature.settings.DiagnosticReportType;
import fr.vriege.anilib.feature.settings.DiagnosticResetArea;
import fr.vriege.anilib.feature.settings.DiagnosticResetPlan;
import fr.vriege.anilib.feature.settings.DiagnosticService;
import fr.vriege.anilib.feature.settings.DiagnosticSnapshot;
import fr.vriege.anilib.feature.settings.StorageUsage;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public final class FileDiagnosticService implements DiagnosticService {
    private static final int MAX_ENTRIES = 100_000;
    private static final int MAX_REPORTS = 100;
    private static final int MAX_REPORT_BYTES = 1024 * 1024;
    private static final int MAX_LOG_MESSAGE = 4_096;
    private static final int MAX_CRASH_DETAILS = 256 * 1024;

    private final Path dataDirectory;
    private final Path diagnosticsDirectory;
    private final Path logsDirectory;
    private final Path crashesDirectory;
    private final Path exportsDirectory;
    private final Map<DiagnosticResetArea, Path> resetTargets;
    private final Map<String, DiagnosticResetPlan> pendingResets = new LinkedHashMap<>();

    public FileDiagnosticService(Path dataDirectory) {
        this.dataDirectory = Objects.requireNonNull(dataDirectory, "dataDirectory must not be null")
                .toAbsolutePath().normalize();
        diagnosticsDirectory = this.dataDirectory.resolve("diagnostics");
        logsDirectory = diagnosticsDirectory.resolve("logs");
        crashesDirectory = diagnosticsDirectory.resolve("crashes");
        exportsDirectory = diagnosticsDirectory.resolve("exports");
        resetTargets = resetTargets();
        createDirectory(this.dataDirectory);
        rejectLink(this.dataDirectory, "data directory");
    }

    @Override
    public synchronized DiagnosticSnapshot snapshot() {
        List<StorageUsage> storage = topLevelUsage();
        long totalBytes = storage.stream().mapToLong(StorageUsage::bytes).sum();
        long totalFiles = storage.stream().mapToLong(StorageUsage::files).sum();
        return new DiagnosticSnapshot(
                Instant.now(),
                dataDirectory,
                totalBytes,
                totalFiles,
                storage,
                reports());
    }

    @Override
    public synchronized void recordLog(String message) {
        String value = bounded(message, "message");
        createDirectory(logsDirectory);
        Path log = logsDirectory.resolve("anilib.log");
        rejectLinkIfPresent(log, "log file");
        rotate(log);
        try {
            Files.writeString(
                    log,
                    Instant.now() + " " + value + System.lineSeparator(),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND);
        } catch (IOException exception) {
            throw failure("record a diagnostic log", exception);
        }
    }

    @Override
    public synchronized void recordCrash(String summary, String details) {
        String safeSummary = bounded(summary, "summary");
        String safeDetails = bounded(details, "details", MAX_CRASH_DETAILS);
        createDirectory(crashesDirectory);
        Path report = crashesDirectory.resolve(Instant.now().toEpochMilli() + "-" + UUID.randomUUID() + ".crash");
        try {
            Files.writeString(
                    report,
                    safeSummary + System.lineSeparator() + safeDetails + System.lineSeparator(),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE_NEW);
        } catch (IOException exception) {
            throw failure("record a crash report", exception);
        }
    }

    @Override
    public synchronized Path export() {
        DiagnosticSnapshot current = snapshot();
        createDirectory(exportsDirectory);
        Path temporary;
        try {
            temporary = Files.createTempFile(exportsDirectory, ".diagnostics-", ".tmp");
        } catch (IOException exception) {
            throw failure("create a diagnostic export", exception);
        }
        Path destination = exportsDirectory.resolve(
                "anilib-diagnostics-" + Instant.now().toEpochMilli() + ".zip");
        try {
            writeExport(temporary, current);
            Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE);
            return destination;
        } catch (IOException exception) {
            throw failure("export diagnostics", exception);
        } finally {
            try {
                Files.deleteIfExists(temporary);
            } catch (IOException ignored) {
                // The primary export operation reports the actionable failure.
            }
        }
    }

    @Override
    public synchronized DiagnosticResetPlan planReset(Set<DiagnosticResetArea> areas) {
        Set<DiagnosticResetArea> checked = Set.copyOf(areas);
        if (checked.isEmpty()) {
            throw new IllegalArgumentException("areas must not be empty");
        }
        List<Path> targets = checked.stream()
                .sorted(Comparator.comparing(Enum::name))
                .map(resetTargets::get)
                .toList();
        long bytes = targets.stream().mapToLong(path -> measure(path).bytes()).sum();
        String token = UUID.randomUUID().toString();
        DiagnosticResetPlan plan = new DiagnosticResetPlan(token, checked, targets, bytes);
        pendingResets.clear();
        pendingResets.put(token, plan);
        return plan;
    }

    @Override
    public synchronized void executeReset(String confirmationToken) {
        DiagnosticResetPlan plan = pendingResets.remove(confirmationToken);
        if (plan == null) {
            throw new IllegalArgumentException("Unknown or expired reset confirmation token");
        }
        plan.targets().forEach(this::deleteTarget);
    }

    private List<StorageUsage> topLevelUsage() {
        try (var entries = Files.list(dataDirectory)) {
            return entries
                    .filter(path -> !Files.isSymbolicLink(path))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .map(path -> {
                        Measurement measurement = measure(path);
                        return new StorageUsage(
                                path.getFileName().toString(),
                                measurement.bytes(),
                                measurement.files());
                    })
                    .toList();
        } catch (IOException exception) {
            throw failure("inspect application storage", exception);
        }
    }

    private List<DiagnosticReport> reports() {
        List<DiagnosticReport> reports = new ArrayList<>();
        addReports(reports, logsDirectory, DiagnosticReportType.LOG);
        addReports(reports, crashesDirectory, DiagnosticReportType.CRASH);
        return reports.stream()
                .sorted(Comparator.comparing(DiagnosticReport::createdAt).reversed())
                .limit(MAX_REPORTS)
                .toList();
    }

    private static void addReports(
            List<DiagnosticReport> reports,
            Path directory,
            DiagnosticReportType type) {
        if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        try (var entries = Files.list(directory)) {
            for (Path file : entries
                    .filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                    .limit(MAX_REPORTS)
                    .toList()) {
                reports.add(new DiagnosticReport(
                        type,
                        file.getFileName().toString(),
                        Files.getLastModifiedTime(file, LinkOption.NOFOLLOW_LINKS).toInstant(),
                        Files.size(file)));
            }
        } catch (IOException exception) {
            throw failure("inspect diagnostic reports", exception);
        }
    }

    private Measurement measure(Path target) {
        if (!Files.exists(target, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(target)) {
            return new Measurement(0, 0);
        }
        if (Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)) {
            try {
                return new Measurement(Files.size(target), 1);
            } catch (IOException exception) {
                throw failure("measure application storage", exception);
            }
        }
        long bytes = 0;
        long files = 0;
        int entries = 0;
        try (var paths = Files.walk(target)) {
            for (Path path : paths.toList()) {
                if (++entries > MAX_ENTRIES) {
                    throw new IllegalStateException("Application storage exceeds the diagnostic entry limit");
                }
                if (Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
                    bytes = Math.addExact(bytes, Files.size(path));
                    files++;
                }
            }
            return new Measurement(bytes, files);
        } catch (IOException | ArithmeticException exception) {
            throw failure("measure application storage", exception);
        }
    }

    private void writeExport(Path destination, DiagnosticSnapshot snapshot) throws IOException {
        try (ZipOutputStream zip = new ZipOutputStream(new BufferedOutputStream(
                Files.newOutputStream(destination, StandardOpenOption.TRUNCATE_EXISTING)))) {
            zip.putNextEntry(new ZipEntry("summary.txt"));
            zip.write(summary(snapshot).getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            addDirectoryToExport(zip, logsDirectory, "logs/");
            addDirectoryToExport(zip, crashesDirectory, "crashes/");
        }
    }

    private static void addDirectoryToExport(ZipOutputStream zip, Path directory, String prefix) throws IOException {
        if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        try (var entries = Files.list(directory)) {
            for (Path file : entries
                    .filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                    .limit(MAX_REPORTS)
                    .toList()) {
                zip.putNextEntry(new ZipEntry(prefix + file.getFileName()));
                try (InputStream input = Files.newInputStream(file)) {
                    byte[] bytes = input.readNBytes(MAX_REPORT_BYTES + 1);
                    if (bytes.length > MAX_REPORT_BYTES) {
                        throw new IOException("Diagnostic report exceeds the export limit");
                    }
                    zip.write(bytes);
                }
                zip.closeEntry();
            }
        }
    }

    private static String summary(DiagnosticSnapshot snapshot) {
        StringBuilder result = new StringBuilder()
                .append("Anilib diagnostics\n")
                .append("inspected-at=").append(snapshot.inspectedAt()).append('\n')
                .append("total-bytes=").append(snapshot.totalBytes()).append('\n')
                .append("total-files=").append(snapshot.totalFiles()).append('\n');
        snapshot.storage().forEach(usage -> result
                .append("storage.").append(usage.area()).append(".bytes=").append(usage.bytes()).append('\n')
                .append("storage.").append(usage.area()).append(".files=").append(usage.files()).append('\n'));
        return result.toString();
    }

    private Map<DiagnosticResetArea, Path> resetTargets() {
        Map<DiagnosticResetArea, Path> targets = new EnumMap<>(DiagnosticResetArea.class);
        targets.put(DiagnosticResetArea.SETTINGS, dataDirectory.resolve("settings.properties"));
        targets.put(DiagnosticResetArea.NETWORK_CACHE, dataDirectory.resolve("http-cache"));
        targets.put(DiagnosticResetArea.LOGS, logsDirectory);
        targets.put(DiagnosticResetArea.CRASH_REPORTS, crashesDirectory);
        return Map.copyOf(targets);
    }

    private void deleteTarget(Path target) {
        Path normalized = target.toAbsolutePath().normalize();
        if (!normalized.startsWith(dataDirectory) || normalized.equals(dataDirectory)) {
            throw new IllegalStateException("Reset target escaped the application data directory");
        }
        if (!Files.exists(normalized, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        if (Files.isSymbolicLink(normalized)) {
            throw new IllegalStateException("Reset targets must not be symbolic links");
        }
        try {
            if (Files.isDirectory(normalized, LinkOption.NOFOLLOW_LINKS)) {
                try (var paths = Files.walk(normalized)) {
                    for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                        if (Files.isSymbolicLink(path)) {
                            Files.delete(path);
                        } else {
                            Files.deleteIfExists(path);
                        }
                    }
                }
            } else {
                Files.delete(normalized);
            }
        } catch (IOException exception) {
            throw failure("reset selected application data", exception);
        }
    }

    private static void rotate(Path log) {
        try {
            if (Files.exists(log, LinkOption.NOFOLLOW_LINKS) && Files.size(log) >= MAX_REPORT_BYTES) {
                Files.move(
                        log,
                        log.resolveSibling("anilib-" + Instant.now().toEpochMilli() + ".log"),
                        StandardCopyOption.ATOMIC_MOVE);
            }
        } catch (IOException exception) {
            throw failure("rotate diagnostic logs", exception);
        }
    }

    private static String bounded(String value, String name) {
        return bounded(value, name, MAX_LOG_MESSAGE);
    }

    private static String bounded(String value, String name, int maximum) {
        String checked = Objects.requireNonNull(value, name + " must not be null").strip();
        if (checked.isEmpty()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return checked.length() <= maximum ? checked : checked.substring(0, maximum);
    }

    private static void createDirectory(Path directory) {
        try {
            Files.createDirectories(directory);
            rejectLink(directory, "diagnostic directory");
        } catch (IOException exception) {
            throw failure("create a diagnostic directory", exception);
        }
    }

    private static void rejectLink(Path path, String name) {
        if (Files.isSymbolicLink(path) || !Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalStateException(name + " must be a real directory");
        }
    }

    private static void rejectLinkIfPresent(Path path, String name) {
        if (Files.exists(path, LinkOption.NOFOLLOW_LINKS)
                && (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(path))) {
            throw new IllegalStateException(name + " must be a regular file");
        }
    }

    private static IllegalStateException failure(String operation, Exception cause) {
        return new IllegalStateException("Unable to " + operation, cause);
    }

    private record Measurement(long bytes, long files) {
    }
}
