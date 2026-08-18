package fr.vriege.anilib.feature.tracker.ui;

import fr.vriege.anilib.feature.library.LibraryItemId;
import fr.vriege.anilib.feature.library.MediaKind;
import fr.vriege.anilib.feature.tracker.TrackerAccount;
import fr.vriege.anilib.feature.tracker.TrackerCredentials;
import fr.vriege.anilib.feature.tracker.TrackerEntry;
import fr.vriege.anilib.feature.tracker.TrackerId;
import fr.vriege.anilib.feature.tracker.TrackerSearchResult;

import java.util.List;

/** Platform-neutral tracking workflow rendered by Android and desktop. */
public interface TrackerPresentation {
    List<TrackerAccount> accounts();

    void authenticate(TrackerId trackerId, TrackerCredentials credentials);

    void logout(TrackerId trackerId);

    List<TrackerSearchResult> search(TrackerId trackerId, String query, MediaKind kind);

    List<TrackerEntry> entries(LibraryItemId itemId);

    TrackerEntry bind(LibraryItemId itemId, TrackerSearchResult result);

    TrackerEntry update(TrackerEntry entry);

    TrackerEntry refresh(LibraryItemId itemId, TrackerId trackerId);

    boolean remove(LibraryItemId itemId, TrackerId trackerId);

    AutoCloseable observe(Runnable listener);
}
