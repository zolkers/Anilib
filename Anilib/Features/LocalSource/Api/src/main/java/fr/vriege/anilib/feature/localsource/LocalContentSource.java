package fr.vriege.anilib.feature.localsource;

import fr.vriege.anilib.feature.source.Source;

import java.util.List;
import java.util.Optional;

public interface LocalContentSource extends Source {
    List<LocalPublication> publications();

    List<LocalPage> pages(LocalPublicationId publicationId);

    byte[] read(LocalPage page);

    Optional<LocalSeriesMetadata> metadata(LocalPublicationId publicationId);

    LocalSourceScan scan();

    LocalSourceScan rescan();
}
