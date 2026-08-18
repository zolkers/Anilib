package fr.vriege.anilib.tooling.archtests;

import fr.vriege.anilib.configuration.standard.StandardAnilib;
import fr.vriege.anilib.feature.library.LibraryCapabilities;
import fr.vriege.anilib.feature.library.LibraryCatalog;
import fr.vriege.anilib.feature.library.LibraryItem;
import fr.vriege.anilib.feature.library.MediaKind;
import fr.vriege.anilib.feature.tracker.Tracker;
import fr.vriege.anilib.feature.tracker.TrackerApiVersion;
import fr.vriege.anilib.feature.tracker.TrackerAuthentication;
import fr.vriege.anilib.feature.tracker.TrackerCapabilities;
import fr.vriege.anilib.feature.tracker.TrackerConflictPolicy;
import fr.vriege.anilib.feature.tracker.TrackerConflictResolution;
import fr.vriege.anilib.feature.tracker.TrackerCredentials;
import fr.vriege.anilib.feature.tracker.TrackerDescriptor;
import fr.vriege.anilib.feature.tracker.TrackerEntry;
import fr.vriege.anilib.feature.tracker.TrackerExtensionManifest;
import fr.vriege.anilib.feature.tracker.TrackerExtensionPlugin;
import fr.vriege.anilib.feature.tracker.TrackerId;
import fr.vriege.anilib.feature.tracker.TrackerSearchResult;
import fr.vriege.anilib.feature.tracker.TrackerService;
import fr.vriege.anilib.feature.tracker.TrackerStatus;
import fr.vriege.anilib.feature.tracker.TrackerSyncDirection;
import fr.vriege.anilib.feature.tracker.TrackerSyncPreferences;
import fr.vriege.anilib.feature.tracker.TrackerSyncReport;
import fr.vriege.anilib.foundation.component.ComponentDescriptor;
import fr.vriege.anilib.kernel.StartedAnilib;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;

final class TrackerSynchronizationTest {
    private TrackerSynchronizationTest() {
    }

    static int run() {
        Counter counter = new Counter();
        Path directory = temporaryDirectory();
        SyncTracker tracker = new SyncTracker();
        LibraryItem item;
        try (StartedAnilib application = StandardAnilib.start(directory, List.of(extension(tracker)))) {
            LibraryCatalog library = application.capability(LibraryCapabilities.CATALOG);
            item = LibraryItem.create("Synchronized anime", MediaKind.ANIME);
            library.save(item);
            TrackerService service = application.capability(TrackerCapabilities.SERVICE);
            TrackerSearchResult candidate = service.search(SyncTracker.ID, "Synchronized", MediaKind.ANIME)
                    .getFirst();
            TrackerEntry bound = service.bind(item.id(), candidate);
            service.synchronizeProgress(item.id(), 3.0D, 12);
            counter.check(service.entries(item.id()).getFirst().progress() == 3.0D
                            && tracker.remote.progress() == bound.progress(),
                    "manual synchronization mode must retain local progress until an explicit sync");

            tracker.remote = tracker.entry(item, 2.0D, TrackerStatus.WATCHING, Instant.now().plusSeconds(10));
            TrackerSyncReport conflicted = service.synchronize(item.id());
            counter.check(conflicted.conflicts().size() == 1 && service.conflicts().size() == 1,
                    "bidirectional ASK policy must retain simultaneous local and remote edits as a conflict");
            TrackerEntry resolved = service.resolveConflict(
                    item.id(), SyncTracker.ID, TrackerConflictResolution.KEEP_LOCAL);
            counter.check(resolved.progress() == 3.0D && tracker.remote.progress() == 3.0D
                            && service.conflicts().isEmpty(),
                    "keeping local must push the pending mirror and clear the conflict");

            tracker.remote = tracker.entry(item, 4.0D, TrackerStatus.WATCHING, Instant.now().plusSeconds(20));
            TrackerSyncReport pulled = service.synchronize(item.id());
            counter.check(pulled.pulled() == 1 && service.entries(item.id()).getFirst().progress() == 4.0D,
                    "a remote-only edit must refresh the durable mirror");

            TrackerSyncPreferences preferences = new TrackerSyncPreferences(
                    true,
                    TrackerSyncDirection.BIDIRECTIONAL,
                    TrackerConflictPolicy.KEEP_REMOTE);
            service.saveSyncPreferences(preferences);
            tracker.remote = tracker.entry(item, 5.0D, TrackerStatus.WATCHING, Instant.now().plusSeconds(30));
            library.save(item.withFavorite(true));
            awaitProgress(service, item, 5.0D);
            counter.check(service.entries(item.id()).getFirst().progress() == 5.0D,
                    "automatic synchronization must refresh bindings after observed library activity");
        }

        try (StartedAnilib restarted = StandardAnilib.start(
                directory,
                List.of(extension(new SyncTracker())))) {
            TrackerService service = restarted.capability(TrackerCapabilities.SERVICE);
            counter.check(service.syncPreferences().automatic()
                            && service.syncPreferences().conflictPolicy() == TrackerConflictPolicy.KEEP_REMOTE,
                    "synchronization direction and conflict preferences must survive restart");
        } finally {
            deleteDirectory(directory);
        }
        return counter.value;
    }

    private static void awaitProgress(TrackerService service, LibraryItem item, double expected) {
        for (int attempt = 0; attempt < 200; attempt++) {
            if (service.entries(item.id()).getFirst().progress() == expected) {
                return;
            }
            try {
                Thread.sleep(10L);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new AssertionError("Interrupted while awaiting automatic tracker synchronization", exception);
            }
        }
        throw new AssertionError("Automatic tracker synchronization did not finish");
    }

    private static TrackerExtensionPlugin extension(SyncTracker tracker) {
        return new TrackerExtensionPlugin(
                TrackerExtensionManifest.offline(
                        ComponentDescriptor.of("tracker.sync-test", "Sync tracker", "1.0.0"),
                        SyncTracker.ID),
                ignored -> tracker);
    }

    private static Path temporaryDirectory() {
        try {
            return Files.createTempDirectory("anilib-tracker-sync-test");
        } catch (IOException exception) {
            throw new AssertionError("Unable to create tracker synchronization test directory", exception);
        }
    }

    private static void deleteDirectory(Path directory) {
        try (java.util.stream.Stream<Path> entries = Files.walk(directory)) {
            for (Path entry : entries.sorted(java.util.Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(entry);
            }
        } catch (IOException exception) {
            throw new AssertionError("Unable to clean tracker synchronization test directory", exception);
        }
    }

    private static final class SyncTracker implements Tracker {
        private static final TrackerId ID = TrackerId.of("sync.test");
        private static final TrackerDescriptor DESCRIPTOR = new TrackerDescriptor(
                ID,
                "Sync test",
                new TrackerApiVersion(1, 0),
                Set.of(MediaKind.ANIME),
                TrackerAuthentication.NONE,
                List.of(TrackerStatus.WATCHING, TrackerStatus.COMPLETED, TrackerStatus.PLANNING),
                List.of(0.0D, 5.0D, 10.0D),
                true,
                true);
        private TrackerEntry remote;

        @Override
        public TrackerDescriptor descriptor() {
            return DESCRIPTOR;
        }

        @Override
        public boolean isAuthenticated() {
            return true;
        }

        @Override
        public String accountName() {
            return "fixture";
        }

        @Override
        public void authenticate(TrackerCredentials credentials) {
            throw new UnsupportedOperationException("Offline fixture does not authenticate");
        }

        @Override
        public void logout() {
        }

        @Override
        public List<TrackerSearchResult> search(String query, MediaKind kind) {
            return List.of(new TrackerSearchResult(
                    ID,
                    "remote-sync-1",
                    "Synchronized anime",
                    kind,
                    12,
                    Optional.of(URI.create("https://tracker.example/anime/1"))));
        }

        @Override
        public TrackerEntry bind(LibraryItem item, TrackerSearchResult result) {
            remote = entry(item, 0.0D, TrackerStatus.PLANNING, Instant.now());
            return remote;
        }

        @Override
        public TrackerEntry update(TrackerEntry entry) {
            remote = entry(
                    new LibraryItem(
                            entry.libraryItemId(),
                            entry.title(),
                            MediaKind.ANIME,
                            Instant.now(),
                            Set.of()),
                    entry.progress(),
                    entry.status(),
                    Instant.now());
            return remote;
        }

        @Override
        public TrackerEntry refresh(TrackerEntry entry) {
            return remote;
        }

        @Override
        public void remove(TrackerEntry entry) {
            remote = null;
        }

        private TrackerEntry entry(
                LibraryItem item,
                double progress,
                TrackerStatus status,
                Instant updatedAt) {
            return new TrackerEntry(
                    item.id(),
                    ID,
                    "remote-sync-1",
                    item.title(),
                    progress,
                    12,
                    status,
                    OptionalDouble.empty(),
                    Optional.empty(),
                    Optional.empty(),
                    false,
                    Optional.of(URI.create("https://tracker.example/anime/1")),
                    updatedAt);
        }
    }

    private static final class Counter {
        private int value;

        private void check(boolean condition, String message) {
            value++;
            if (!condition) {
                throw new AssertionError(message);
            }
        }
    }
}
