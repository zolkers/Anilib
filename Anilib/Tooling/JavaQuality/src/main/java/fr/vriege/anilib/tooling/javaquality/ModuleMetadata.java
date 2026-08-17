package fr.vriege.anilib.tooling.javaquality;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/** Parsed architectural intent from one module.properties file. */
public record ModuleMetadata(
        String id,
        Layer layer,
        String role,
        String owner,
        Language language,
        List<String> dependencies,
        Path directory,
        Path manifestPath) {

    public ModuleMetadata {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(layer, "layer must not be null");
        Objects.requireNonNull(role, "role must not be null");
        Objects.requireNonNull(owner, "owner must not be null");
        Objects.requireNonNull(language, "language must not be null");
        dependencies = List.copyOf(dependencies);
        Objects.requireNonNull(directory, "directory must not be null");
        Objects.requireNonNull(manifestPath, "manifestPath must not be null");
    }

    public enum Language {
        JAVA,
        JAVA_KOTLIN,
        KOTLIN
    }

    public enum Layer {
        FOUNDATION(0),
        FRAMEWORK(1),
        KERNEL(2),
        FEATURE(3),
        EXTENSION(3),
        CONFIGURATION(4),
        PLATFORM(5),
        TOOLING(6);

        private final int rank;

        Layer(int rank) {
            this.rank = rank;
        }

        public int rank() {
            return rank;
        }
    }
}
