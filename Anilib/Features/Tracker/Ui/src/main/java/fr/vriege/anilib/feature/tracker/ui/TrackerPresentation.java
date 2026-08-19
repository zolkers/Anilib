package fr.vriege.anilib.feature.tracker.ui;

import fr.vriege.anilib.feature.library.LibraryItemId;
import fr.vriege.anilib.feature.library.MediaKind;
import fr.vriege.anilib.feature.tracker.TrackerAccount;
import fr.vriege.anilib.feature.tracker.TrackerAuthorization;
import fr.vriege.anilib.feature.tracker.TrackerCredentials;
import fr.vriege.anilib.feature.tracker.TrackerConflictResolution;
import fr.vriege.anilib.feature.tracker.TrackerEntry;
import fr.vriege.anilib.feature.tracker.TrackerId;
import fr.vriege.anilib.feature.tracker.TrackerSearchResult;
import fr.vriege.anilib.feature.tracker.TrackerSyncConflict;
import fr.vriege.anilib.feature.tracker.TrackerSyncPreferences;
import fr.vriege.anilib.feature.tracker.TrackerSyncReport;

import java.net.URI;
import java.util.List;

public interface TrackerPresentation {
    List<TrackerAccount> accounts();

    void authenticate(TrackerId trackerId, TrackerCredentials credentials);

    TrackerAuthorization beginAuthorization(TrackerId trackerId);

    void completeAuthorization(TrackerId trackerId, URI callbackUri);

    void logout(TrackerId trackerId);

    List<TrackerSearchResult> search(TrackerId trackerId, String query, MediaKind kind);

    List<TrackerEntry> entries(LibraryItemId itemId);

    TrackerEntry bind(LibraryItemId itemId, TrackerSearchResult result);

    TrackerEntry update(TrackerEntry entry);

    TrackerEntry refresh(LibraryItemId itemId, TrackerId trackerId);

    boolean remove(LibraryItemId itemId, TrackerId trackerId);

    TrackerSyncPreferences syncPreferences();

    void saveSyncPreferences(TrackerSyncPreferences preferences);

    TrackerSyncReport synchronizeAll();

    TrackerSyncReport synchronize(LibraryItemId itemId);

    List<TrackerSyncConflict> conflicts();

    TrackerEntry resolveConflict(
            LibraryItemId itemId,
            TrackerId trackerId,
            TrackerConflictResolution resolution);

    AutoCloseable observe(Runnable listener);
}
