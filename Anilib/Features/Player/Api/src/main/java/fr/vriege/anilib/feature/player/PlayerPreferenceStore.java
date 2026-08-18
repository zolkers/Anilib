package fr.vriege.anilib.feature.player;

import fr.vriege.anilib.feature.library.LibraryItemId;

public interface PlayerPreferenceStore {
    PlayerPreferences snapshot();

    PlayerPreferences snapshot(LibraryItemId libraryItemId);

    boolean hasOverride(LibraryItemId libraryItemId);

    void save(PlayerPreferences preferences);

    void saveOverride(LibraryItemId libraryItemId, PlayerPreferences preferences);

    void clearOverride(LibraryItemId libraryItemId);
}
