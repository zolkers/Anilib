package fr.vriege.anilib.feature.extensionrepository.runtime;

import fr.vriege.anilib.foundation.validation.Preconditions;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Atomic preference for the opt-in automatic portable-source update channel. */
public final class FileExtensionUpdatePolicyStore {
    private final Path file;

    public FileExtensionUpdatePolicyStore(Path file) {
        this.file = Preconditions.requireNonNull(file, "file").toAbsolutePath().normalize();
    }

    public boolean load() {
        if (!Files.exists(file)) {
            return false;
        }
        try {
            String value = Files.readString(file, StandardCharsets.UTF_8).strip();
            if (value.equals("enabled=true")) {
                return true;
            }
            if (value.equals("enabled=false")) {
                return false;
            }
            throw new IllegalStateException("Malformed extension update policy");
        } catch (IOException exception) {
            throw new UncheckedIOException("Unable to read extension update policy " + file, exception);
        }
    }

    public void save(boolean enabled) {
        Path parent = file.getParent();
        Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
        try {
            Files.createDirectories(parent);
            Files.writeString(temporary, "enabled=" + enabled + System.lineSeparator(), StandardCharsets.UTF_8);
            try {
                Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            throw new UncheckedIOException("Unable to write extension update policy " + file, exception);
        }
    }
}
