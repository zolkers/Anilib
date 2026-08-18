package fr.vriege.anilib.feature.tracker;

import fr.vriege.anilib.feature.library.LibraryItem;
import fr.vriege.anilib.feature.library.MediaKind;

import java.util.List;

/** Provider adapter implemented by an explicitly selected tracker bundle. */
public interface Tracker {
    TrackerDescriptor descriptor();

    boolean isAuthenticated();

    String accountName();

    void authenticate(TrackerCredentials credentials);

    void logout();

    List<TrackerSearchResult> search(String query, MediaKind kind);

    TrackerEntry bind(LibraryItem item, TrackerSearchResult result);

    TrackerEntry update(TrackerEntry entry);

    TrackerEntry refresh(TrackerEntry entry);

    void remove(TrackerEntry entry);
}
