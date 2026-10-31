package fr.vriege.anilib.platform.desktopextensionhost.extension;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Properties;

/* Journals the APK/JAR pair so failed and interrupted updates retain the previous version. */
final class ExtensionInstallTransaction {
    private ExtensionInstallTransaction() {}

    static void commit(Path stagedApk, Path stagedJar, Path apk, Path jar) throws IOException {
        Path transaction = Files.createTempDirectory(apk.getParent(), ".anilib-update-");
        Properties manifest = new Properties();
        manifest.setProperty("package", apk.getFileName().toString().replaceFirst("\\.apk$", ""));
        try {
            backup(apk, transaction.resolve("previous.apk"), manifest, "apk");
            backup(jar, transaction.resolve("previous.jar"), manifest, "jar");
            try (var output = Files.newOutputStream(transaction.resolve("manifest"))) {
                manifest.store(output, null);
            }
            Files.createFile(transaction.resolve("pending"));
            replace(stagedJar, jar);
            replace(stagedApk, apk);
            Files.delete(transaction.resolve("pending"));
        } catch (IOException failure) {
            try {
                recoverOne(transaction);
            } catch (IOException rollback) {
                failure.addSuppressed(rollback);
            }
            throw failure;
        }
        try {
            cleanup(transaction);
        } catch (IOException cleanupFailure) {
            // Both files are committed. Startup can remove this completed journal later.
            System.getLogger(ExtensionInstallTransaction.class.getName())
                    .log(
                            System.Logger.Level.WARNING,
                            "Unable to clean completed extension update",
                            cleanupFailure);
        }
    }

    static void recover(Path directory) throws IOException {
        try (var entries = Files.list(directory)) {
            for (Path transaction :
                    entries.filter(
                                    path ->
                                            path.getFileName()
                                                    .toString()
                                                    .startsWith(".anilib-update-"))
                            .toList()) {
                if (!Files.isDirectory(transaction, LinkOption.NOFOLLOW_LINKS)
                        || Files.isSymbolicLink(transaction)) {
                    throw new IOException("Invalid extension update journal");
                }
                recoverOne(transaction);
                cleanup(transaction);
            }
        }
    }

    private static void backup(Path source, Path backup, Properties manifest, String kind)
            throws IOException {
        boolean exists = Files.exists(source, LinkOption.NOFOLLOW_LINKS);
        manifest.setProperty(kind, Boolean.toString(exists));
        if (exists) {
            requireRegular(source);
            Files.copy(source, backup);
        }
    }

    private static void recoverOne(Path transaction) throws IOException {
        Path pending = transaction.resolve("pending");
        if (!Files.exists(pending, LinkOption.NOFOLLOW_LINKS)) return;
        requireRegular(pending);
        Path manifestFile = transaction.resolve("manifest");
        requireRegular(manifestFile);
        Properties manifest = new Properties();
        try (var input = Files.newInputStream(manifestFile)) {
            manifest.load(input);
        }
        String packageName = manifest.getProperty("package", "");
        if (!packageName.matches("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+")) {
            throw new IOException("Invalid extension update package");
        }
        for (String kind : List.of("apk", "jar")) {
            Path target = transaction.getParent().resolve(packageName + '.' + kind);
            if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) requireRegular(target);
            String hadPrevious = manifest.getProperty(kind);
            if ("true".equals(hadPrevious)) {
                Path previous = transaction.resolve("previous." + kind);
                requireRegular(previous);
                Files.copy(previous, target, StandardCopyOption.REPLACE_EXISTING);
            } else if ("false".equals(hadPrevious)) {
                Files.deleteIfExists(target);
            } else throw new IOException("Invalid extension update journal state");
        }
        Files.delete(pending);
    }

    private static void cleanup(Path transaction) throws IOException {
        for (String name : List.of("previous.apk", "previous.jar", "manifest", "pending")) {
            Path file = transaction.resolve(name);
            if (Files.exists(file, LinkOption.NOFOLLOW_LINKS)) {
                requireRegular(file);
                Files.delete(file);
            }
        }
        Files.delete(transaction);
    }

    private static void requireRegular(Path path) throws IOException {
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(path)) {
            throw new IOException(
                    "Extension update target is not a regular file: " + path.getFileName());
        }
    }

    private static void replace(Path source, Path destination) throws IOException {
        if (Files.exists(destination, LinkOption.NOFOLLOW_LINKS)) requireRegular(destination);
        try {
            Files.move(
                    source,
                    destination,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(source, destination, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
