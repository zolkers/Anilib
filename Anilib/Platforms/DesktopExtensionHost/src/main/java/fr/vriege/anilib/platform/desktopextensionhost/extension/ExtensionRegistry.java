package fr.vriege.anilib.platform.desktopextensionhost.extension;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.function.Consumer;
import java.util.jar.JarFile;
import java.util.stream.Stream;

public final class ExtensionRegistry {
    private final Path directory;
    private final ApkMetadataReader metadataReader;
    private final ExtensionArchivePreparer preparer;
    private volatile List<InstalledExtension> installedCache;
    private final Object mutations = new Object();

    public ExtensionRegistry(Path dataDirectory) {
        this(dataDirectory, new ApkMetadataReader(), new ExtensionArchivePreparer());
    }

    ExtensionRegistry(
            Path dataDirectory,
            ApkMetadataReader metadataReader,
            ExtensionArchivePreparer preparer) {
        this.directory =
                prepareDirectory(
                        Objects.requireNonNull(dataDirectory, "dataDirectory")
                                .toAbsolutePath()
                                .normalize()
                                .resolve("extensions"));
        this.metadataReader = Objects.requireNonNull(metadataReader, "metadataReader");
        this.preparer = Objects.requireNonNull(preparer, "preparer");
        try {
            ExtensionInstallTransaction.recover(directory);
        } catch (IOException exception) {
            throw new UncheckedIOException("Unable to recover extension update", exception);
        }
    }

    public InstalledExtension install(Path downloadedApk) {
        return install(
                downloadedApk,
                installed -> {
                    try (ExtensionRuntimeCatalog preflight = new ExtensionRuntimeCatalog(this)) {
                        preflight.validate(installed);
                    }
                },
                Runnable::run);
    }

    public InstalledExtension install(
            Path downloadedApk, Consumer<InstalledExtension> validate, Consumer<Runnable> commit) {
        synchronized (mutations) {
            return installPrepared(downloadedApk, validate, commit);
        }
    }

    private InstalledExtension installPrepared(
            Path downloadedApk, Consumer<InstalledExtension> validate, Consumer<Runnable> commit) {
        ExtensionApkMetadata metadata = metadataReader.read(downloadedApk);
        Path apk = file(metadata.packageName(), ".apk");
        Path archive = file(metadata.packageName(), ".jar");
        try {
            Path temporaryApk = Files.createTempFile(directory, ".anilib-install-", ".apk");
            Path temporaryArchive = Files.createTempFile(directory, ".anilib-install-", ".jar");
            try {
                Files.copy(downloadedApk, temporaryApk, StandardCopyOption.REPLACE_EXISTING);
                preparer.prepare(temporaryApk, temporaryArchive);
                try {
                    validate.accept(
                            new InstalledExtension(metadata, temporaryApk, temporaryArchive));
                } catch (RuntimeException | LinkageError failure) {
                    throw new ExtensionInstallationException(
                            "This extension requires capabilities not available in this version of"
                                + " Anilib. The previous version was preserved. See the diagnostic"
                                + " log for details.",
                            failure);
                }
                commit.accept(
                        () -> {
                            synchronized (this) {
                                installedCache = null;
                                try {
                                    ExtensionInstallTransaction.commit(
                                            temporaryApk, temporaryArchive, apk, archive);
                                } catch (IOException exception) {
                                    throw new UncheckedIOException(
                                            "Unable to commit extension update", exception);
                                }
                            }
                        });
                return new InstalledExtension(metadata, apk, archive);
            } finally {
                Files.deleteIfExists(temporaryApk);
                Files.deleteIfExists(temporaryArchive);
            }
        } catch (IOException exception) {
            throw new UncheckedIOException("Unable to install extension", exception);
        }
    }

    public synchronized boolean uninstall(String packageName) {
        ExtensionApkMetadata metadata =
                find(packageName).map(InstalledExtension::metadata).orElse(null);
        if (metadata == null) {
            return false;
        }
        try {
            installedCache = null;
            Files.deleteIfExists(file(metadata.packageName(), ".jar"));
            try (Stream<Path> files = Files.list(directory)) {
                for (Path apk : files.filter(ExtensionRegistry::apkFile).toList()) {
                    try {
                        if (metadataReader.read(apk).packageName().equals(metadata.packageName())) {
                            Files.deleteIfExists(apk);
                        }
                    } catch (IllegalArgumentException ignored) {
                    }
                }
            }
            return true;
        } catch (IOException exception) {
            throw new UncheckedIOException("Unable to uninstall extension", exception);
        }
    }

    public synchronized Optional<InstalledExtension> find(String packageName) {
        String requested = Objects.requireNonNull(packageName, "packageName");
        return installed().stream()
                .filter(extension -> extension.metadata().packageName().equals(requested))
                .findFirst();
    }

    public synchronized List<InstalledExtension> installed() {
        if (installedCache != null) {
            return installedCache;
        }
        Map<String, InstalledExtension> result = new LinkedHashMap<>();
        try (Stream<Path> files = Files.list(directory)) {
            for (Path apk :
                    files.filter(ExtensionRegistry::apkFile)
                            .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                            .toList()) {
                if (!Files.isRegularFile(apk, LinkOption.NOFOLLOW_LINKS)
                        || Files.isSymbolicLink(apk)) {
                    continue;
                }
                try {
                    ExtensionApkMetadata metadata = metadataReader.read(apk);
                    Path archive = file(metadata.packageName(), ".jar");
                    if (Files.isRegularFile(archive, LinkOption.NOFOLLOW_LINKS)
                            && !Files.isSymbolicLink(archive)) {
                        result.put(
                                metadata.packageName(),
                                new InstalledExtension(metadata, apk, archive));
                    }
                } catch (IllegalArgumentException ignored) {
                    // A damaged entry is quarantined by omission; it cannot take down the engine.
                }
            }
            installedCache = List.copyOf(result.values());
            return installedCache;
        } catch (IOException exception) {
            throw new UncheckedIOException("Unable to list installed extensions", exception);
        }
    }

    public synchronized int prepareInstalledArchives() {
        int prepared = 0;
        try (Stream<Path> files = Files.list(directory)) {
            Map<String, Path> apks = new LinkedHashMap<>();
            for (Path apk : files.filter(ExtensionRegistry::apkFile).sorted().toList()) {
                if (!Files.isRegularFile(apk, LinkOption.NOFOLLOW_LINKS)
                        || Files.isSymbolicLink(apk)) {
                    continue;
                }
                try {
                    ExtensionApkMetadata metadata = metadataReader.read(apk);
                    apks.putIfAbsent(metadata.packageName(), apk);
                } catch (IllegalArgumentException ignored) {
                }
            }
            for (Map.Entry<String, Path> entry : apks.entrySet()) {
                if (!preparedArchive(file(entry.getKey(), ".jar"))) {
                    try {
                        install(entry.getValue());
                        prepared++;
                    } catch (RuntimeException | LinkageError failure) {
                        System.getLogger(ExtensionRegistry.class.getName())
                                .log(
                                        System.Logger.Level.WARNING,
                                        "Unable to prepare extension "
                                                + entry.getKey()
                                                + "; previous files preserved",
                                        failure);
                    }
                }
            }
            return prepared;
        } catch (IOException exception) {
            throw new UncheckedIOException("Unable to prepare installed extensions", exception);
        }
    }

    private static boolean apkFile(Path path) {
        return path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".apk");
    }

    private static boolean preparedArchive(Path archive) {
        if (!Files.isRegularFile(archive, LinkOption.NOFOLLOW_LINKS)
                || Files.isSymbolicLink(archive)) {
            return false;
        }
        try (JarFile jar = new JarFile(archive.toFile())) {
            var marker = jar.getJarEntry("META-INF/anilib-desktop-extension.properties");
            if (marker == null) {
                return false;
            }
            Properties properties = new Properties();
            try (var input = jar.getInputStream(marker)) {
                properties.load(input);
            }
            return ExtensionBytecodeRelocator.FORMAT.equals(properties.getProperty("format"));
        } catch (IOException exception) {
            return false;
        }
    }

    private Path file(String packageName, String suffix) {
        Path result = directory.resolve(packageName + suffix).normalize();
        if (!result.getParent().equals(directory)) {
            throw new IllegalArgumentException("Extension package escapes the registry");
        }
        return result;
    }

    private static Path prepareDirectory(Path directory) {
        try {
            if (Files.exists(directory, LinkOption.NOFOLLOW_LINKS)
                    && Files.isSymbolicLink(directory)) {
                throw new IllegalArgumentException(
                        "Extension registry must not be a symbolic link");
            }
            Files.createDirectories(directory);
            if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
                throw new IllegalArgumentException("Extension registry path is not a directory");
            }
            return directory;
        } catch (IOException exception) {
            throw new UncheckedIOException("Unable to prepare extension registry", exception);
        }
    }
}
