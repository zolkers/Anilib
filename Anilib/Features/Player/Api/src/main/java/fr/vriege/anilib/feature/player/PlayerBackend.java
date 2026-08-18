package fr.vriege.anilib.feature.player;

public interface PlayerBackend {
    String id();

    boolean available();

    PlayerPlayback open(PlayerMedia media);
}
