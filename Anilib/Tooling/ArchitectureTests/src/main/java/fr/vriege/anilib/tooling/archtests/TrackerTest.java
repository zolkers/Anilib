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
import java.util.concurrent.atomic.AtomicInteger;

/** Executable contract for opt-in tracker bundles and durable Aniyomi-style mutations. */
final class TrackerTest {
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
            counter.check(application.capability(TrackerCapabilities.REGISTRY).extensions().size() == 1,
                    "selected tracker bundles must register through the SDK registrar");
            counter.check(service.accounts().size() == 1 && !service.accounts().getFirst().authenticated(),
                    "tracker accounts must expose their logged-out state");
            service.authenticate(TestTracker.ID, TrackerCredentials.password("alice", "secret"));
            counter.check(service.accounts().getFirst().authenticated()
                            && service.accounts().getFirst().accountName().equals("alice"),
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
            } catch (fr.vriege.anilib.kernel.PluginStartupException expected) {
                counter.check(hasCause(expected, TrackerException.class),
                        "tracker registry must reject incompatible extension API versions");
            }
        } finally {
            deleteDirectory(incompatibleDirectory);
        }
        return counter.value;
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
        try (java.util.stream.Stream<Path> entries = Files.walk(directory)) {
            for (Path entry : entries.sorted(java.util.Comparator.reverseOrder()).toList()) {
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
