package fr.vriege.anilib.platform.desktopextensionhost.extension;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;

public final class ExtensionArchivePreparer {
    private final ApkMetadataReader metadataReader;
    private final ExtensionBytecodeRelocator relocator;

    public ExtensionArchivePreparer() {
        this(new ApkMetadataReader(), new ExtensionBytecodeRelocator());
    }

    ExtensionArchivePreparer(ApkMetadataReader metadataReader, ExtensionBytecodeRelocator relocator) {
        this.metadataReader = Objects.requireNonNull(metadataReader, "metadataReader");
        this.relocator = Objects.requireNonNull(relocator, "relocator");
    }

    public PreparedExtension prepare(Path apk, Path destination) {
        ExtensionApkMetadata metadata = metadataReader.read(apk);
        Path target = destination.toAbsolutePath().normalize();
        Path parent = Objects.requireNonNull(target.getParent(), "destination parent");
        try {
            Files.createDirectories(parent);
            Path converted = Files.createTempFile(parent, ".anilib-dex-", ".jar");
            Path relocated = Files.createTempFile(parent, ".anilib-relocated-", ".jar");
            try {
                convert(apk, converted);
                ExtensionBytecodeRelocator.RelocationResult result = relocator.relocate(converted, relocated);
                replace(relocated, target);
                return new PreparedExtension(metadata, target, result);
            } finally {
                Files.deleteIfExists(converted);
                Files.deleteIfExists(relocated);
            }
        } catch (IOException exception) {
            throw new UncheckedIOException("Unable to prepare extension archive", exception);
        }
    }

    private static void convert(Path apk, Path destination) {
        ExtensionToolBridge.convertDex(apk, destination);
    }

    private static void replace(Path source, Path destination) throws IOException {
        try {
            Files.move(source, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(source, destination, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public record PreparedExtension(
            ExtensionApkMetadata metadata,
            Path archive,
            ExtensionBytecodeRelocator.RelocationResult relocation) {
        public PreparedExtension {
            metadata = Objects.requireNonNull(metadata, "metadata");
            archive = Objects.requireNonNull(archive, "archive");
            relocation = Objects.requireNonNull(relocation, "relocation");
        }
    }
}
