package fr.vriege.anilib.feature.tracker;

import fr.vriege.anilib.feature.library.LibraryItemId;
import fr.vriege.anilib.feature.library.MediaKind;

import java.util.Collection;
import java.util.List;

public interface TrackerService {
    List<TrackerAccount> accounts();

    void authenticate(TrackerId trackerId, TrackerCredentials credentials);

    void logout(TrackerId trackerId);

    List<TrackerSearchResult> search(TrackerId trackerId, String query, MediaKind kind);

    List<TrackerEntry> entries(LibraryItemId libraryItemId);

    TrackerEntry bind(LibraryItemId libraryItemId, TrackerSearchResult result);

    TrackerEntry update(TrackerEntry entry);

    TrackerEntry refresh(LibraryItemId libraryItemId, TrackerId trackerId);

    boolean remove(LibraryItemId libraryItemId, TrackerId trackerId);

    void synchronizeProgress(LibraryItemId libraryItemId, double progress, long totalUnits);

    TrackerSyncPreferences syncPreferences();

    void saveSyncPreferences(TrackerSyncPreferences preferences);

    TrackerSyncReport synchronizeAll();

    TrackerSyncReport synchronize(LibraryItemId libraryItemId);

    List<TrackerSyncConflict> conflicts();

    TrackerEntry resolveConflict(
            LibraryItemId libraryItemId,
            TrackerId trackerId,
            TrackerConflictResolution resolution);

    List<TrackerEntry> snapshot();

    void replaceAll(Collection<TrackerEntry> entries);

    AutoCloseable observe(Runnable listener);
}
