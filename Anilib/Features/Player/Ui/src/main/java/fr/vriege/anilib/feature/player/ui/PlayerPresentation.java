package fr.vriege.anilib.feature.player.ui;

import fr.vriege.anilib.feature.library.LibraryItemId;
import fr.vriege.anilib.feature.player.EpisodeSnapshot;
import fr.vriege.anilib.feature.source.SourceEpisodeId;
import fr.vriege.anilib.feature.source.SourceCatalogueItemId;

import java.util.List;

public interface PlayerPresentation {
    boolean canOpen(LibraryItemId libraryItemId);

    List<EpisodeSnapshot> episodes(LibraryItemId libraryItemId);

    List<EpisodeSnapshot> episodes(SourceCatalogueItemId itemId);

    PlayerController open(LibraryItemId libraryItemId, SourceEpisodeId episodeId);

    PlayerController open(String title, SourceEpisodeId episodeId);

    AutoCloseable observe(Runnable listener);
}
