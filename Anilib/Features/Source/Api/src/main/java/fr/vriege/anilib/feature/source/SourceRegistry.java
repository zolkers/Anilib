package fr.vriege.anilib.feature.source;

import java.util.List;
import java.util.Optional;

/** Read-only deterministic view of sources installed in the current product. */
public interface SourceRegistry {
    List<Source> sources();

    List<InstalledSourceExtension> extensions();

    Optional<Source> find(SourceId id);
}
