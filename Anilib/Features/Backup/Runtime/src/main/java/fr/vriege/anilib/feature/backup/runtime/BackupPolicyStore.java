package fr.vriege.anilib.feature.backup.runtime;

import fr.vriege.anilib.feature.backup.BackupPolicy;
import fr.vriege.anilib.feature.backup.BackupSchedule;
import fr.vriege.anilib.framework.backup.BackupSectionId;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.Arrays;
import java.util.List;

final class BackupPolicyStore {
    private final Path file;
    private final BackupPolicy defaults;

    BackupPolicyStore(Path file, BackupPolicy defaults) {
        this.file = file.toAbsolutePath().normalize();
        this.defaults = defaults;
    }

    synchronized State load() {
        if (!Files.exists(file)) {
            return new State(defaults, Optional.empty());
        }
        Map<String, String> values = new LinkedHashMap<>();
        try {
            for (String row : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                String[] columns = row.split("=", 2);
                if (columns.length != 2 || values.putIfAbsent(columns[0], columns[1]) != null) {
                    throw new IllegalStateException("Invalid backup policy row");
                }
            }
            Set<BackupSectionId> sections = Arrays.stream(required(values, "sections").split(","))
                    .filter(value -> !value.isBlank())
                    .map(BackupSectionId::of)
                    .collect(Collectors.toUnmodifiableSet());
            BackupPolicy policy = new BackupPolicy(
                    BackupSchedule.valueOf(required(values, "schedule")),
                    Integer.parseInt(required(values, "retention")),
                    sections,
                    Path.of(decode(required(values, "destination"))));
            Optional<Instant> lastAutomatic = Optional.ofNullable(values.get("lastAutomatic"))
                    .filter(value -> !value.isBlank())
                    .map(Instant::parse);
            return new State(policy, lastAutomatic);
        } catch (IOException exception) {
            throw new UncheckedIOException("Unable to read backup policy", exception);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("Invalid backup policy value", exception);
        }
    }

    synchronized void save(State state) {
        BackupPolicy policy = state.policy();
        List<String> rows = List.of(
                "destination=" + encode(policy.destination().toString()),
                "lastAutomatic=" + state.lastAutomatic().map(Instant::toString).orElse(""),
                "retention=" + policy.retentionCount(),
                "schedule=" + policy.schedule().name(),
                "sections=" + policy.includedSections().stream()
                        .map(BackupSectionId::value)
                        .sorted()
                        .collect(Collectors.joining(",")));
        Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
        try {
            Files.createDirectories(file.getParent());
            Files.write(temporary, rows, StandardCharsets.UTF_8);
            moveAtomically(temporary, file);
        } catch (IOException exception) {
            throw new UncheckedIOException("Unable to write backup policy", exception);
        } finally {
            try {
                Files.deleteIfExists(temporary);
            } catch (IOException ignored) {
                // The primary operation reports the actionable error.
            }
        }
    }

    private static String required(Map<String, String> values, String name) {
        String value = values.get(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing backup policy value: " + name);
        }
        return value;
    }

    private static String encode(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String decode(String value) {
        return new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
    }

    private static void moveAtomically(Path source, Path destination) throws IOException {
        try {
            Files.move(source, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(source, destination, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    record State(BackupPolicy policy, Optional<Instant> lastAutomatic) {
    }
}
