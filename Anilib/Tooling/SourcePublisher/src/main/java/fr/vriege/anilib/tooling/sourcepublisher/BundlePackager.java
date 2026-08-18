package fr.vriege.anilib.tooling.sourcepublisher;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Reproducibly packages compiled extension classes with the required descriptor. */
final class BundlePackager {
    private static final String DESCRIPTOR = "META-INF/anilib-extension.properties";

    private BundlePackager() {
    }

    static void pack(Path classesDirectory, Path descriptorFile, Path outputFile) {
        Path classes = classesDirectory.toAbsolutePath().normalize();
        if (!Files.isDirectory(classes) || Files.isSymbolicLink(classes)) {
            throw new IllegalArgumentException("Classes path must be one regular directory: " + classes);
        }
        byte[] descriptor = PublisherFiles.read(descriptorFile, "descriptor");
        List<Path> entries;
        try (java.util.stream.Stream<Path> paths = Files.walk(classes)) {
            entries = paths.filter(path -> !path.equals(classes))
                    .sorted(Comparator.comparing(path -> archiveName(classes, path)))
                    .toList();
        } catch (IOException exception) {
            throw new UncheckedIOException("Unable to inspect compiled classes " + classes, exception);
        }
        if (entries.stream().anyMatch(Files::isSymbolicLink)) {
            throw new IllegalArgumentException("Compiled classes must not contain symbolic links");
        }
        if (entries.stream().map(path -> archiveName(classes, path)).anyMatch(DESCRIPTOR::equals)) {
            throw new IllegalArgumentException("Classes already contain " + DESCRIPTOR);
        }
        Path output = outputFile.toAbsolutePath().normalize();
        Path parent = output.getParent();
        if (parent == null) {
            throw new IllegalArgumentException("Bundle output must have a parent");
        }
        Path temporary = parent.resolve(output.getFileName() + ".tmp");
        try {
            Files.createDirectories(parent);
            try (ZipOutputStream archive = new ZipOutputStream(
                    new BufferedOutputStream(Files.newOutputStream(temporary)))) {
                for (Path entry : entries) {
                    if (Files.isDirectory(entry)) {
                        continue;
                    }
                    if (!Files.isRegularFile(entry)) {
                        throw new IllegalArgumentException("Unsupported compiled entry: " + entry);
                    }
                    write(archive, archiveName(classes, entry), Files.readAllBytes(entry));
                }
                write(archive, DESCRIPTOR, descriptor);
            }
            PublisherFiles.write(output, Files.readAllBytes(temporary));
            Files.deleteIfExists(temporary);
        } catch (IOException exception) {
            throw new UncheckedIOException("Unable to package source Bundle " + output, exception);
        }
    }

    private static void write(ZipOutputStream archive, String name, byte[] content) throws IOException {
        ZipEntry entry = new ZipEntry(name);
        entry.setTime(0L);
        archive.putNextEntry(entry);
        archive.write(content);
        archive.closeEntry();
    }

    private static String archiveName(Path root, Path entry) {
        return root.relativize(entry).toString().replace('\\', '/');
    }
}
