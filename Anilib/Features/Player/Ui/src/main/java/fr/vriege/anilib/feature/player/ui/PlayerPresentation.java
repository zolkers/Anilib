package fr.vriege.anilib.feature.player.ui;

import fr.vriege.anilib.feature.library.LibraryItemId;
import fr.vriege.anilib.feature.player.EpisodeSnapshot;
import fr.vriege.anilib.feature.source.SourceEpisodeId;

import java.util.List;

/** Platform-neutral entry point consumed by Android and desktop anime screens. */
public interface PlayerPresentation {
    boolean canOpen(LibraryItemId libraryItemId);

    List<EpisodeSnapshot> episodes(LibraryItemId libraryItemId);

    PlayerController open(LibraryItemId libraryItemId, SourceEpisodeId episodeId);

    AutoCloseable observe(Runnable listener);
}
