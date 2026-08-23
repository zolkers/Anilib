package fr.vriege.anilib.tooling.archtests;

import fr.vriege.anilib.configuration.standard.StandardAnilib;
import fr.vriege.anilib.feature.library.LibraryCapabilities;
import fr.vriege.anilib.feature.library.LibraryCatalog;
import fr.vriege.anilib.feature.library.LibraryItem;
import fr.vriege.anilib.feature.library.LibraryOrigin;
import fr.vriege.anilib.feature.library.MediaKind;
import fr.vriege.anilib.feature.player.PlayerCapabilities;
import fr.vriege.anilib.feature.source.SourceCatalogueItemId;
import fr.vriege.anilib.feature.source.SourceContentKind;
import fr.vriege.anilib.feature.source.SourceDescriptor;
import fr.vriege.anilib.feature.source.SourceEpisode;
import fr.vriege.anilib.feature.source.SourceEpisodeId;
import fr.vriege.anilib.feature.source.SourceExtensionPlugin;
import fr.vriege.anilib.feature.source.SourceId;
import fr.vriege.anilib.feature.source.SourceSdk;
import fr.vriege.anilib.feature.source.SourceVideoStream;
import fr.vriege.anilib.feature.source.StreamingSource;
import fr.vriege.anilib.feature.tracker.Tracker;
import fr.vriege.anilib.feature.tracker.TrackerApiVersion;
import fr.vriege.anilib.feature.tracker.TrackerAuthentication;
import fr.vriege.anilib.feature.tracker.TrackerCapabilities;
import fr.vriege.anilib.feature.tracker.TrackerCredentials;
import fr.vriege.anilib.feature.tracker.TrackerDescriptor;
import fr.vriege.anilib.feature.tracker.TrackerEntry;
import fr.vriege.anilib.feature.tracker.TrackerException;
import fr.vriege.anilib.feature.tracker.TrackerExtensionManifest;
import fr.vriege.anilib.feature.tracker.TrackerExtensionPlugin;
import fr.vriege.anilib.feature.tracker.TrackerId;
import fr.vriege.anilib.feature.tracker.TrackerSearchResult;
import fr.vriege.anilib.feature.tracker.TrackerService;
import fr.vriege.anilib.feature.tracker.TrackerStatus;
import fr.vriege.anilib.foundation.component.ComponentDescriptor;
import fr.vriege.anilib.framework.backup.BackupSectionCodec;
import fr.vriege.anilib.kernel.StartedAnilib;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import fr.vriege.anilib.kernel.PluginStartupException;
import java.util.Comparator;
import java.util.stream.Stream;

final class TrackerTest {
    private static final SourceId PLAYBACK_SOURCE_ID = SourceId.of("test.tracker-playback");
    private static final SourceCatalogueItemId PLAYBACK_ITEM_ID =
            new SourceCatalogueItemId(PLAYBACK_SOURCE_ID, "tracked-anime");
    private static final SourceEpisode PLAYBACK_EPISODE = new SourceEpisode(
            new SourceEpisodeId(PLAYBACK_ITEM_ID, "episode-7"),
            "Episode 7",
            7.0d,
            Optional.empty(),
            Optional.empty());

    private TrackerTest() {
    }

    static int run() {
        Counter counter = new Counter();
        Path directory = temporaryDirectory();
        LibraryItem item;
        TestTracker first = new TestTracker();
        try (StartedAnilib application = StandardAnilib.start(
                directory,
                List.of(extension(first)))) {
            LibraryCatalog library = application.capability(LibraryCapabilities.CATALOG);
            item = LibraryItem.create("Tracked anime", MediaKind.ANIME);
            library.save(item);
            TrackerService service = application.capability(TrackerCapabilities.SERVICE);
            counter.check(application.capability(TrackerCapabilities.REGISTRY).extensions().size() == 2,
                    "first-party and additional tracker bundles must register through the SDK registrar");
            var testAccount = service.accounts().stream()
                    .filter(account -> account.descriptor().id().equals(TestTracker.ID))
                    .findFirst().orElseThrow();
            counter.check(service.accounts().size() == 2 && !testAccount.authenticated(),
                    "tracker accounts must expose their logged-out state");
            service.authenticate(TestTracker.ID, TrackerCredentials.password("alice", "secret"));
            testAccount = service.accounts().stream()
                    .filter(account -> account.descriptor().id().equals(TestTracker.ID))
                    .findFirst().orElseThrow();
            counter.check(testAccount.authenticated() && testAccount.accountName().equals("alice"),
                    "tracker authentication must expose the provider account name");
            TrackerSearchResult candidate = service.search(TestTracker.ID, "Tracked", MediaKind.ANIME)
                    .getFirst();
            TrackerEntry bound = service.bind(item.id(), candidate);
            counter.check(bound.status() == TrackerStatus.PLANNING && service.entries(item.id()).size() == 1,
                    "search results must bind to one durable title entry");
            TrackerEntry edited = bound
                    .withStatus(TrackerStatus.WATCHING)
                    .withProgress(3.0)
                    .withScore(OptionalDouble.of(8.0))
                    .withDates(Optional.of(LocalDate.of(2026, 8, 1)), Optional.empty())
                    .withPrivateEntry(true);
            TrackerEntry updated = service.update(edited);
            counter.check(updated.progress() == 3.0 && updated.score().orElseThrow() == 8.0
                            && updated.privateEntry() && updated.startDate().isPresent(),
                    "status, progress, score, dates, and privacy must update through the provider");
            TrackerEntry refreshed = service.refresh(item.id(), TestTracker.ID);
            counter.check(refreshed.title().equals("Tracked anime refreshed") && first.refreshes.get() == 1,
                    "refresh must replace the local mirror with validated remote state");
            service.synchronizeProgress(item.id(), 4.0, TrackerSearchResult.UNKNOWN_TOTAL);
            counter.check(service.entries(item.id()).getFirst().progress() == 4.0
                            && service.entries(item.id()).getFirst().totalUnits() == 12,
                    "activity progress must preserve the tracker total when the Player does not know it");
            service.synchronizeProgress(item.id(), 12.0, 12);
            counter.check(service.entries(item.id()).getFirst().status() == TrackerStatus.COMPLETED
                            && service.entries(item.id()).getFirst().finishDate().isPresent(),
                    "completed local progress must synchronize completion and finish date");
            BackupSectionCodec backup = application.capability(TrackerCapabilities.BACKUP_CODEC);
            counter.check(backup.inspect(backup.currentVersion(), backup.exportSection().payload()).entryCount() == 1,
                    "tracking backup must inspect its feature-owned durable section");
        }

        TestTracker second = new TestTracker();
        second.authenticated = true;
        second.account = "alice";
        try (StartedAnilib application = StandardAnilib.start(directory, List.of(extension(second)))) {
            TrackerService service = application.capability(TrackerCapabilities.SERVICE);
            counter.check(service.entries(item.id()).size() == 1
                            && service.entries(item.id()).getFirst().progress() == 12.0,
                    "tracker entries must survive a complete product restart");
            counter.check(service.remove(item.id(), TestTracker.ID) && second.removals.get() == 1,
                    "unlink must delete remotely before removing the durable local binding");
            counter.check(service.entries(item.id()).isEmpty(),
                    "removed tracker bindings must disappear from the title");
        } finally {
            deleteDirectory(directory);
        }

        TestTracker unsupported = new TestTracker(new TrackerApiVersion(2, 0));
        Path incompatibleDirectory = temporaryDirectory();
        try {
            try (StartedAnilib incompatible = StandardAnilib.start(
                    incompatibleDirectory,
                    List.of(extension(unsupported)))) {
                incompatible.components();
                throw new AssertionError("Expected incompatible tracker API rejection");
            } catch (PluginStartupException expected) {
                counter.check(hasCause(expected, TrackerException.class),
                        "tracker registry must reject incompatible extension API versions");
            }
        } finally {
            deleteDirectory(incompatibleDirectory);
        }
        advancesTrackerFromCompletedPlayback(counter);
        return counter.value;
    }

    private static void advancesTrackerFromCompletedPlayback(Counter counter) {
        Path directory = temporaryDirectory();
        TestTracker tracker = new TestTracker();
        tracker.authenticated = true;
        tracker.account = "alice";
        try (StartedAnilib application = StandardAnilib.start(
                directory,
                List.of(extension(tracker), playbackSourceExtension()))) {
            LibraryItem item = LibraryItem.create("Tracked anime", MediaKind.ANIME)
                    .withOrigin(new LibraryOrigin(
                            PLAYBACK_SOURCE_ID.toString(),
                            PLAYBACK_ITEM_ID.value()));
            application.capability(LibraryCapabilities.CATALOG).save(item);
            TrackerService service = application.capability(TrackerCapabilities.SERVICE);
            service.bind(item.id(), service.search(TestTracker.ID, item.title(), item.kind()).getFirst());
            CountDownLatch progressObserved = new CountDownLatch(1);
            AutoCloseable observation = service.observe(progressObserved::countDown);
            try {
                application.capability(PlayerCapabilities.SERVICE).setEpisodesCompleted(
                        item.id(),
                        Set.of(PLAYBACK_EPISODE.id()),
                        true);
                counter.check(progressObserved.await(3L, TimeUnit.SECONDS),
                        "completed playback must asynchronously update the local tracker entry");
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new AssertionError("Interrupted while awaiting playback tracking", exception);
            } finally {
                closeObservation(observation);
            }
            TrackerEntry tracked = service.entries(item.id()).getFirst();
            counter.check(tracked.progress() == PLAYBACK_EPISODE.episodeNumber()
                            && tracked.totalUnits() == 12,
                    "playback tracking must advance to the episode number without losing the remote total");
        } finally {
            deleteDirectory(directory);
        }
    }

    private static SourceExtensionPlugin playbackSourceExtension() {
        return new SourceExtensionPlugin(
                ComponentDescriptor.of("extension.tracker-playback", "Tracker playback", "1.0.0"),
                new PlaybackStreamingSource());
    }

    private static TrackerExtensionPlugin extension(TestTracker tracker) {
        return new TrackerExtensionPlugin(
                TrackerExtensionManifest.offline(
                        ComponentDescriptor.of("tracker.test", "Test Tracker", "1.0.0"),
                        TestTracker.ID),
                ignored -> tracker);
    }

    private static Path temporaryDirectory() {
        try {
            return Files.createTempDirectory("anilib-tracker-test");
        } catch (IOException exception) {
            throw new AssertionError("Unable to create tracker test directory", exception);
        }
    }

    private static void deleteDirectory(Path directory) {
        try (Stream<Path> entries = Files.walk(directory)) {
            for (Path entry : entries.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(entry);
            }
        } catch (IOException exception) {
            throw new AssertionError("Unable to clean tracker test directory", exception);
        }
    }

    private static boolean hasCause(Throwable failure, Class<? extends Throwable> type) {
        Throwable current = failure;
        while (current != null) {
            if (type.isInstance(current)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private static void closeObservation(AutoCloseable observation) {
        try {
            observation.close();
        } catch (Exception exception) {
            throw new AssertionError("Unable to close tracker progress observation", exception);
        }
    }

    private static final class PlaybackStreamingSource implements StreamingSource {
        @Override
        public SourceDescriptor descriptor() {
            return new SourceDescriptor(
                    PLAYBACK_SOURCE_ID,
                    "Tracker playback",
                    "1.0.0",
                    "en",
                    Set.of(SourceContentKind.ANIME),
                    SourceSdk.API_VERSION);
        }

        @Override
        public List<SourceEpisode> episodes(SourceCatalogueItemId itemId) {
            return List.of(PLAYBACK_EPISODE);
        }

        @Override
        public List<SourceVideoStream> streams(SourceEpisodeId episodeId) {
            return List.of();
        }
    }

    private static final class TestTracker implements Tracker {
        private static final TrackerId ID = TrackerId.of("test.tracker");
        private final TrackerDescriptor descriptor;
        private final AtomicInteger refreshes = new AtomicInteger();
        private final AtomicInteger removals = new AtomicInteger();
        private boolean authenticated;
        private String account = "";

        private TestTracker() {
            this(new TrackerApiVersion(1, 0));
        }

        private TestTracker(TrackerApiVersion version) {
            descriptor = new TrackerDescriptor(
                    ID,
                    "Test Tracker",
                    version,
                    Set.of(MediaKind.ANIME, MediaKind.MANGA),
                    TrackerAuthentication.USERNAME_PASSWORD,
                    List.of(
                            TrackerStatus.WATCHING,
                            TrackerStatus.READING,
                            TrackerStatus.COMPLETED,
                            TrackerStatus.ON_HOLD,
                            TrackerStatus.PLANNING,
                            TrackerStatus.DROPPED),
                    List.of(0.0, 2.0, 4.0, 6.0, 8.0, 10.0),
                    true,
                    true);
        }

        @Override
        public TrackerDescriptor descriptor() {
            return descriptor;
        }

        @Override
        public boolean isAuthenticated() {
            return authenticated;
        }

        @Override
        public String accountName() {
            return account;
        }

        @Override
        public void authenticate(TrackerCredentials credentials) {
            authenticated = true;
            account = credentials.identity();
        }

        @Override
        public void logout() {
            authenticated = false;
            account = "";
        }

        @Override
        public List<TrackerSearchResult> search(String query, MediaKind kind) {
            return List.of(new TrackerSearchResult(
                    ID, "remote-1", "Tracked anime", kind, 12,
                    Optional.of(URI.create("https://tracker.example/anime/1"))));
        }

        @Override
        public TrackerEntry bind(LibraryItem item, TrackerSearchResult result) {
            return new TrackerEntry(
                    item.id(), ID, result.remoteId(), result.title(), 0.0, result.totalUnits(),
                    TrackerStatus.PLANNING, OptionalDouble.empty(), Optional.empty(), Optional.empty(),
                    false, result.remoteUri(), Instant.now());
        }

        @Override
        public TrackerEntry update(TrackerEntry entry) {
            return entry;
        }

        @Override
        public TrackerEntry refresh(TrackerEntry entry) {
            refreshes.incrementAndGet();
            return new TrackerEntry(
                    entry.libraryItemId(), entry.trackerId(), entry.remoteId(),
                    "Tracked anime refreshed", entry.progress(), entry.totalUnits(), entry.status(),
                    entry.score(), entry.startDate(), entry.finishDate(), entry.privateEntry(),
                    entry.remoteUri(), Instant.now());
        }

        @Override
        public void remove(TrackerEntry entry) {
            removals.incrementAndGet();
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
