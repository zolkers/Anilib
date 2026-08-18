package fr.vriege.anilib.feature.extensionrepository.runtime;

import fr.vriege.anilib.foundation.validation.Preconditions;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

public final class FileExtensionRepositoryStore {
    private final Path file;

    public FileExtensionRepositoryStore(Path file) {
        this.file = Preconditions.requireNonNull(file, "file").toAbsolutePath().normalize();
    }

    public List<URI> load() {
        if (!Files.exists(file)) {
            return List.of();
        }
        try {
            List<URI> repositories = new ArrayList<>();
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                String value = line.strip();
                if (!value.isEmpty()) {
                    repositories.add(AniyomiRepositoryIndexParser.requireRepositoryUri(URI.create(value)));
                }
            }
            if (repositories.stream().distinct().count() != repositories.size()) {
                throw new IllegalStateException("Repository store contains duplicate URLs");
            }
            return List.copyOf(repositories);
        } catch (IOException exception) {
            throw new UncheckedIOException("Unable to read extension repository store " + file, exception);
        }
    }

    public void save(List<URI> repositories) {
        List<URI> values = List.copyOf(Preconditions.requireNonNull(repositories, "repositories"));
        String content = String.join(
                System.lineSeparator(),
                values.stream().map(URI::toASCIIString).toList());
        if (!content.isEmpty()) {
            content += System.lineSeparator();
        }
        Path parent = file.getParent();
        Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
        try {
            Files.createDirectories(parent);
            Files.writeString(temporary, content, StandardCharsets.UTF_8);
            moveAtomically(temporary, file);
        } catch (IOException exception) {
            throw new UncheckedIOException("Unable to write extension repository store " + file, exception);
        }
    }

    private void moveAtomically(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
