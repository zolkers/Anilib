package fr.vriege.anilib.feature.player;

import fr.vriege.anilib.feature.library.LibraryItemId;
import fr.vriege.anilib.feature.source.SourceEpisodeId;

import java.util.List;

public interface PlayerService {
    boolean canOpen(LibraryItemId libraryItemId);

    List<EpisodeSnapshot> episodes(LibraryItemId libraryItemId);

    PlayerSession open(LibraryItemId libraryItemId, SourceEpisodeId episodeId);

    AutoCloseable observe(Runnable listener);
}
