package fr.vriege.anilib.feature.source;

import java.util.List;
import java.util.Optional;

public interface SourceRegistry {
    List<Source> sources();

    List<InstalledSourceExtension> extensions();

    Optional<Source> find(SourceId id);
}
