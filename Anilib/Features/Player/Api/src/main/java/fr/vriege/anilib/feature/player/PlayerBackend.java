package fr.vriege.anilib.feature.player;

/** Platform-owned factory for one isolated media playback handle. */
public interface PlayerBackend {
    String id();

    boolean available();

    PlayerPlayback open(PlayerMedia media);
}
