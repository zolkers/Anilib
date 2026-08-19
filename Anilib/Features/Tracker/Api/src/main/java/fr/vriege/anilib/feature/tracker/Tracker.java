package fr.vriege.anilib.feature.tracker;

import fr.vriege.anilib.feature.library.LibraryItem;
import fr.vriege.anilib.feature.library.MediaKind;

import java.net.URI;
import java.util.List;
import java.util.Optional;

public interface Tracker {
    TrackerDescriptor descriptor();

    boolean isAuthenticated();

    String accountName();

    void authenticate(TrackerCredentials credentials);

    default Optional<TrackerAuthorization> beginAuthorization() {
        return Optional.empty();
    }

    default void completeAuthorization(URI callbackUri) {
        throw new TrackerException("Web authorization is not supported by " + descriptor().name());
    }

    void logout();

    List<TrackerSearchResult> search(String query, MediaKind kind);

    TrackerEntry bind(LibraryItem item, TrackerSearchResult result);

    TrackerEntry update(TrackerEntry entry);

    TrackerEntry refresh(TrackerEntry entry);

    void remove(TrackerEntry entry);
}
