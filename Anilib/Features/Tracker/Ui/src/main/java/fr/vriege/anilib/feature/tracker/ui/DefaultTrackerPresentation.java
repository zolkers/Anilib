package fr.vriege.anilib.feature.tracker.ui;

import fr.vriege.anilib.feature.library.LibraryItemId;
import fr.vriege.anilib.feature.library.MediaKind;
import fr.vriege.anilib.feature.tracker.TrackerAccount;
import fr.vriege.anilib.feature.tracker.TrackerCredentials;
import fr.vriege.anilib.feature.tracker.TrackerEntry;
import fr.vriege.anilib.feature.tracker.TrackerId;
import fr.vriege.anilib.feature.tracker.TrackerSearchResult;
import fr.vriege.anilib.feature.tracker.TrackerService;

import java.util.List;
import java.util.Objects;

public final class DefaultTrackerPresentation implements TrackerPresentation {
    private final TrackerService service;

    public DefaultTrackerPresentation(TrackerService service) {
        this.service = Objects.requireNonNull(service, "service must not be null");
    }

    @Override
    public List<TrackerAccount> accounts() {
        return service.accounts();
    }

    @Override
    public void authenticate(TrackerId trackerId, TrackerCredentials credentials) {
        service.authenticate(trackerId, credentials);
    }

    @Override
    public void logout(TrackerId trackerId) {
        service.logout(trackerId);
    }

    @Override
    public List<TrackerSearchResult> search(TrackerId trackerId, String query, MediaKind kind) {
        return service.search(trackerId, query, kind);
    }

    @Override
    public List<TrackerEntry> entries(LibraryItemId itemId) {
        return service.entries(itemId);
    }

    @Override
    public TrackerEntry bind(LibraryItemId itemId, TrackerSearchResult result) {
        return service.bind(itemId, result);
    }

    @Override
    public TrackerEntry update(TrackerEntry entry) {
        return service.update(entry);
    }

    @Override
    public TrackerEntry refresh(LibraryItemId itemId, TrackerId trackerId) {
        return service.refresh(itemId, trackerId);
    }

    @Override
    public boolean remove(LibraryItemId itemId, TrackerId trackerId) {
        return service.remove(itemId, trackerId);
    }

    @Override
    public AutoCloseable observe(Runnable listener) {
        return service.observe(listener);
    }
}
