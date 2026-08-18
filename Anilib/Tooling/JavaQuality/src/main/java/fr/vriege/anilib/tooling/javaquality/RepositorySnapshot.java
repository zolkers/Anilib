package fr.vriege.anilib.tooling.javaquality;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

public record RepositorySnapshot(
        Path root,
        List<ModuleMetadata> modules,
        List<JavaSource> javaSources,
        List<KotlinSource> kotlinSources,
        List<Path> buildFiles) {

    public RepositorySnapshot {
        Objects.requireNonNull(root, "root must not be null");
        modules = List.copyOf(modules);
        javaSources = List.copyOf(javaSources);
        kotlinSources = List.copyOf(kotlinSources);
        buildFiles = List.copyOf(buildFiles);
    }
}
