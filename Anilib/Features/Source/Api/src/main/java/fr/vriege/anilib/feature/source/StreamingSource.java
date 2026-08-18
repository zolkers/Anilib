package fr.vriege.anilib.feature.source;

import java.util.List;

public interface StreamingSource extends Source {
    List<SourceEpisode> episodes(SourceCatalogueItemId itemId);

    List<SourceVideoStream> streams(SourceEpisodeId episodeId);
}
