package fr.vriege.anilib.feature.source;

import java.util.List;

/** Optional source capability exposing episodes and playable stream candidates. */
public interface StreamingSource extends Source {
    List<SourceEpisode> episodes(SourceCatalogueItemId itemId);

    List<SourceVideoStream> streams(SourceEpisodeId episodeId);
}
