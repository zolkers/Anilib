package fr.vriege.anilib.feature.player;

import fr.vriege.anilib.feature.library.LibraryItemId;
import fr.vriege.anilib.feature.source.SourceEpisodeId;
import fr.vriege.anilib.feature.source.SourceCatalogueItemId;

import java.util.List;
import java.util.Collection;
import java.util.function.Consumer;

public interface PlayerService {
    boolean canOpen(LibraryItemId libraryItemId);

    List<EpisodeSnapshot> episodes(LibraryItemId libraryItemId);

    List<EpisodeSnapshot> episodes(SourceCatalogueItemId itemId);

    List<EpisodeSnapshot> setEpisodesCompleted(
            LibraryItemId libraryItemId,
            Collection<SourceEpisodeId> episodeIds,
            boolean completed);

    PlayerSession open(LibraryItemId libraryItemId, SourceEpisodeId episodeId);

    PlayerSession open(String title, SourceEpisodeId episodeId);

    AutoCloseable observe(Runnable listener);

    AutoCloseable observeProgress(Consumer<PlayerProgressEvent> listener);
}
