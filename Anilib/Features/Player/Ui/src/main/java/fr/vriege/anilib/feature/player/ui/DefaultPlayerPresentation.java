package fr.vriege.anilib.feature.player.ui;

import fr.vriege.anilib.feature.library.LibraryItemId;
import fr.vriege.anilib.feature.player.EpisodeSnapshot;
import fr.vriege.anilib.feature.player.PlayerService;
import fr.vriege.anilib.feature.source.SourceEpisodeId;

import java.util.List;
import java.util.Objects;

/** Default presentation adapter over the shared Player service. */
public final class DefaultPlayerPresentation implements PlayerPresentation {
    private final PlayerService player;

    public DefaultPlayerPresentation(PlayerService player) {
        this.player = Objects.requireNonNull(player, "player must not be null");
    }

    @Override
    public boolean canOpen(LibraryItemId libraryItemId) {
        return player.canOpen(libraryItemId);
    }

    @Override
    public List<EpisodeSnapshot> episodes(LibraryItemId libraryItemId) {
        return player.episodes(libraryItemId);
    }

    @Override
    public PlayerController open(LibraryItemId libraryItemId, SourceEpisodeId episodeId) {
        return new PlayerController(player.open(libraryItemId, episodeId));
    }

    @Override
    public AutoCloseable observe(Runnable listener) {
        return player.observe(listener);
    }
}
