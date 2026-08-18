package fr.vriege.anilib.tooling.sourcepublisher;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Small atomic-file boundary shared by publisher commands. */
final class PublisherFiles {
    private PublisherFiles() {
    }

    static void write(Path target, byte[] content) {
        Path output = target.toAbsolutePath().normalize();
        Path parent = requireParent(output);
        Path temporary = parent.resolve(output.getFileName() + ".tmp");
        try {
            Files.createDirectories(parent);
            Files.write(temporary, content);
            move(temporary, output);
        } catch (IOException exception) {
            throw new UncheckedIOException("Unable to write " + output, exception);
        }
    }

    static void writeText(Path target, String content) {
        write(target, content.getBytes(StandardCharsets.UTF_8));
    }

    static byte[] read(Path path, String label) {
        Path input = path.toAbsolutePath().normalize();
        if (!Files.isRegularFile(input) || Files.isSymbolicLink(input)) {
            throw new IllegalArgumentException(label + " must be one regular file: " + input);
        }
        try {
            return Files.readAllBytes(input);
        } catch (IOException exception) {
            throw new UncheckedIOException("Unable to read " + input, exception);
        }
    }

    private static Path requireParent(Path path) {
        Path parent = path.getParent();
        if (parent == null) {
            throw new IllegalArgumentException("Output path must have a parent: " + path);
        }
        return parent;
    }

    private static void move(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
