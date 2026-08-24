package fr.vriege.anilib.feature.player;

import fr.vriege.anilib.feature.source.SourceCatalogueItemId;
import fr.vriege.anilib.feature.source.SourceEpisode;
import fr.vriege.anilib.feature.source.SourceEpisodeId;
import fr.vriege.anilib.feature.source.SourceVideoStream;

import java.util.List;

public interface PlayerContentProvider {
    List<SourceEpisode> episodes(SourceCatalogueItemId itemId);

    List<SourceVideoStream> streams(SourceEpisodeId episodeId);

    boolean sourceFallbackAllowed();
}
