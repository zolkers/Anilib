package fr.vriege.anilib.feature.player;

public interface PlayerContentRegistrar {
    AutoCloseable register(PlayerContentProvider provider);
}
