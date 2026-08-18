package fr.vriege.anilib.feature.reader;

import fr.vriege.anilib.feature.library.LibraryItemId;

public interface ReaderDisplayPreferenceStore {
    ReaderDisplayPreferences snapshot();

    ReaderDisplayPreferences snapshot(LibraryItemId libraryItemId);

    boolean hasOverride(LibraryItemId libraryItemId);

    void save(ReaderDisplayPreferences preferences);

    void saveOverride(LibraryItemId libraryItemId, ReaderDisplayPreferences preferences);

    void clearOverride(LibraryItemId libraryItemId);
}
