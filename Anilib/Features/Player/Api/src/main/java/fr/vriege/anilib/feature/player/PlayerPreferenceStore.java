package fr.vriege.anilib.feature.player;

import fr.vriege.anilib.feature.library.LibraryItemId;

public interface PlayerPreferenceStore {
    float volume();

    PlayerPreferences snapshot();

    PlayerPreferences snapshot(LibraryItemId libraryItemId);

    boolean hasOverride(LibraryItemId libraryItemId);

    void save(PlayerPreferences preferences);

    void saveVolume(float volume);

    void saveOverride(LibraryItemId libraryItemId, PlayerPreferences preferences);

    void clearOverride(LibraryItemId libraryItemId);
}
