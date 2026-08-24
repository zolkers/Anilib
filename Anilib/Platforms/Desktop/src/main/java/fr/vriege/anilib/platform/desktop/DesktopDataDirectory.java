package fr.vriege.anilib.platform.desktop;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.CopyOption;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;

final class DesktopDataDirectory {
    private static final String DURABLE_WINDOWS_DIRECTORY = "AnilibData";
    private static final String LEGACY_WINDOWS_DIRECTORY = "Anilib";
    private static final Set<String> LEGACY_PROGRAM_ENTRIES = Set.of("app", "runtime", "anilib.exe");

    private DesktopDataDirectory() {
    }

    static Path resolve() {
        String configured = System.getProperty("anilib.dataDirectory");
        if (configured != null && !configured.isBlank()) {
            return Path.of(configured).toAbsolutePath().normalize();
        }
        String operatingSystem = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (operatingSystem.contains("win")) {
            String localAppData = System.getenv("LOCALAPPDATA");
            if (localAppData != null && !localAppData.isBlank()) {
                return migrateLegacyWindowsData(Path.of(localAppData));
            }
        }
        if (operatingSystem.contains("mac")) {
            return Path.of(System.getProperty("user.home"), "Library", "Application Support", "Anilib");
        }
        String xdgDataHome = System.getenv("XDG_DATA_HOME");
        if (xdgDataHome != null && !xdgDataHome.isBlank()) {
            return Path.of(xdgDataHome, "anilib");
        }
        return Path.of(System.getProperty("user.home"), ".local", "share", "anilib");
    }

    static Path migrateLegacyWindowsData(Path localAppData) {
        Path local = localAppData.toAbsolutePath().normalize();
        Path legacy = local.resolve(LEGACY_WINDOWS_DIRECTORY);
        Path durable = local.resolve(DURABLE_WINDOWS_DIRECTORY);
        if (!Files.isDirectory(legacy, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(legacy)) {
            return durable;
        }
        try {
            Files.createDirectories(durable);
            try (Stream<Path> children = Files.list(legacy)) {
                for (Path child : children.toList()) {
                    String name = child.getFileName().toString();
                    if (!LEGACY_PROGRAM_ENTRIES.contains(name.toLowerCase(Locale.ROOT))) {
                        copyNewerTree(child, durable.resolve(name));
                    }
                }
            }
            return durable;
        } catch (IOException exception) {
            throw new UncheckedIOException("Unable to preserve legacy Anilib user data", exception);
        }
    }

    private static void copyNewerTree(Path source, Path target) throws IOException {
        if (Files.isSymbolicLink(source)) {
            return;
        }
        if (Files.isDirectory(source, LinkOption.NOFOLLOW_LINKS)) {
            Files.createDirectories(target);
            try (Stream<Path> children = Files.list(source)) {
                for (Path child : children.toList()) {
                    copyNewerTree(child, target.resolve(child.getFileName().toString()));
                }
            }
            return;
        }
        if (!Files.isRegularFile(source, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        if (Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)
                && Files.getLastModifiedTime(target).compareTo(Files.getLastModifiedTime(source)) >= 0) {
            return;
        }
        Files.createDirectories(target.getParent());
        CopyOption[] options = {StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES};
        Files.copy(source, target, options);
    }
}
