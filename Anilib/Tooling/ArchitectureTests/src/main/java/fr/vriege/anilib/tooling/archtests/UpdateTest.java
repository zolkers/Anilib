package fr.vriege.anilib.tooling.archtests;

import fr.vriege.anilib.configuration.standard.StandardAnilib;
import fr.vriege.anilib.feature.backup.BackupCapabilities;
import fr.vriege.anilib.feature.library.LibraryCapabilities;
import fr.vriege.anilib.feature.library.LibraryCategory;
import fr.vriege.anilib.feature.library.LibraryCategoryUpdatePolicy;
import fr.vriege.anilib.feature.library.LibraryConfigurationSnapshot;
import fr.vriege.anilib.feature.library.LibraryDisplayDensity;
import fr.vriege.anilib.feature.library.LibraryDisplayMode;
import fr.vriege.anilib.feature.library.LibraryDisplayPreferences;
import fr.vriege.anilib.feature.library.LibraryItem;
import fr.vriege.anilib.feature.library.LibraryItemId;
import fr.vriege.anilib.feature.library.LibraryOrigin;
import fr.vriege.anilib.feature.library.MediaKind;
import fr.vriege.anilib.feature.library.LibrarySort;
import fr.vriege.anilib.feature.player.PlayerBackends;
import fr.vriege.anilib.feature.network.NetworkStatus;
import fr.vriege.anilib.feature.settings.ui.SettingsUiCapabilities;
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
import fr.vriege.anilib.feature.updates.LibraryUpdateNotification;
import fr.vriege.anilib.feature.updates.LibraryUpdateSkipReason;
import fr.vriege.anilib.feature.updates.LibraryUpdateNotificationType;
import fr.vriege.anilib.feature.updates.LibraryUpdateNotifier;
import fr.vriege.anilib.feature.updates.LibraryUpdatePolicy;
import fr.vriege.anilib.feature.updates.LibraryUpdateService;
import fr.vriege.anilib.feature.updates.LibraryUpdateSnapshot;
import fr.vriege.anilib.feature.updates.LibraryUpdateStatus;
import fr.vriege.anilib.feature.updates.UpdateCapabilities;
import fr.vriege.anilib.feature.updates.UpdateInterval;
import fr.vriege.anilib.foundation.component.ComponentDescriptor;
import fr.vriege.anilib.framework.http.runtime.UrlConnectionHttpTransport;
import fr.vriege.anilib.kernel.StartedAnilib;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicInteger;

final class UpdateTest {
    private static final SourceId SOURCE_ID = SourceId.of("test.updates");
    private static final SourceCatalogueItemId SOURCE_ITEM_ID =
            new SourceCatalogueItemId(SOURCE_ID, "series");

    private UpdateTest() {
    }

    static int run() {
        Counter counter = new Counter();
        enforcesNetworkPolicy(counter);
        enforcesCategoryPolicies(counter);
        cleansOrphanedUpdateState(counter);
        Path sourceDirectory = temporaryDirectory("anilib-updates-source");
        Path targetDirectory = temporaryDirectory("anilib-updates-target");
        MutableStreamingSource source = new MutableStreamingSource(List.of(episode("episode-1", 1.0d)));
        RecordingNotifier notifier = new RecordingNotifier();
        Path backup;
        try {
            try (StartedAnilib application = start(sourceDirectory, source, notifier)) {
                LibraryItem item = new LibraryItem(
                        new LibraryItemId("updates-title"),
                        "Update title",
                        MediaKind.ANIME,
                        Instant.parse("2026-08-01T00:00:00Z"),
                        Set.of("Seasonal"))
                        .withFavorite(true)
                        .withOrigin(new LibraryOrigin(SOURCE_ID.toString(), SOURCE_ITEM_ID.value()));
                application.capability(LibraryCapabilities.CATALOG).save(item);
                LibraryUpdateService updates = application.capability(UpdateCapabilities.SERVICE);
                updates.configure(new LibraryUpdatePolicy(
                        UpdateInterval.MANUAL, true, true, false, Set.of("Seasonal"), Set.of()));

                LibraryUpdateSnapshot baseline = updates.runNow().join();
                counter.check(baseline.status() == LibraryUpdateStatus.COMPLETED && baseline.events().isEmpty(),
                        "the first refresh must establish a silent source baseline");
                source.replace(List.of(episode("episode-2", 2.0d), episode("episode-1", 1.0d)));
                LibraryUpdateSnapshot detected = updates.runNow().join();
                counter.check(detected.events().size() == 1
                                && detected.events().getFirst().sourceContentId().endsWith("episode-2")
                                && detected.unreadCount() == 1,
                        "a later refresh must detect and retain only newly published episodes");
                counter.check(notifier.notifications.stream()
                                .anyMatch(value -> value.type() == LibraryUpdateNotificationType.NEW_CONTENT),
                        "new content must be sent through the selected platform notifier");
                var eventId = detected.events().getFirst().id();
                updates.setEventsRead(Set.of(eventId), true);
                counter.check(updates.snapshot().unreadCount() == 0,
                        "selected update events must be markable as read");
                updates.setEventsRead(Set.of(eventId), false);
                counter.check(updates.snapshot().unreadCount() == 1,
                        "selected update events must be markable as unread");

                CountDownLatch entered = new CountDownLatch(1);
                CountDownLatch release = new CountDownLatch(1);
                source.block(entered, release);
                CompletableFuture<LibraryUpdateSnapshot> first = updates.runNow();
                await(entered, "background update did not enter the test source");
                CompletableFuture<LibraryUpdateSnapshot> second = updates.runNow();
                counter.check(first == second, "concurrent refresh requests must share the single running job");
                release.countDown();
                first.join();
                source.unblock();

                updates.markAllRead();
                counter.check(updates.snapshot().unreadCount() == 0,
                        "the Updates feed must persist its read state");
                LibraryUpdatePolicy currentPolicy = updates.snapshot().policy();
                updates.configure(new LibraryUpdatePolicy(
                        currentPolicy.interval(),
                        currentPolicy.favoritesOnly(),
                        currentPolicy.skipCompleted(),
                        currentPolicy.skipNotStarted(),
                        currentPolicy.includedCategories(),
                        currentPolicy.excludedCategories(),
                        Set.of(item.id()),
                        Set.of()));
                backup = application.capability(BackupCapabilities.SERVICE).createBackup().path();
            }

            try (StartedAnilib restarted = start(sourceDirectory, source, new RecordingNotifier())) {
                LibraryUpdateService restartedUpdates = restarted.capability(UpdateCapabilities.SERVICE);
                LibraryUpdateSnapshot durable = restartedUpdates.snapshot();
                counter.check(durable.events().size() == 1 && durable.unreadCount() == 0,
                        "update discoveries and read state must survive a complete product restart");
                counter.check(durable.policy().interval() == UpdateInterval.MANUAL
                                && durable.policy().favoritesOnly()
                                && durable.policy().includedTitles().contains(new LibraryItemId("updates-title")),
                        "the automatic update policy and title exceptions must survive a complete restart");
                restartedUpdates.removeEvents(Set.of(durable.events().getFirst().id()));
                counter.check(restartedUpdates.snapshot().events().isEmpty(),
                        "selected update events must be removable from the durable feed");
            }

            try (StartedAnilib restored = start(
                    targetDirectory,
                    new MutableStreamingSource(List.of(episode("episode-2", 2.0d), episode("episode-1", 1.0d))),
                    new RecordingNotifier())) {
                restored.capability(BackupCapabilities.SERVICE).restore(backup);
                LibraryUpdateSnapshot snapshot = restored.capability(UpdateCapabilities.SERVICE).snapshot();
                counter.check(snapshot.events().size() == 1 && snapshot.policy().interval() == UpdateInterval.MANUAL,
                        "backup restore must merge update history and reschedule the imported policy");
            }
            counter.check(Files.isRegularFile(sourceDirectory.resolve("library-updates.anilib")),
                    "the Updates feature must own one atomic durable state file");
        } finally {
            deleteDirectory(sourceDirectory);
            deleteDirectory(targetDirectory);
        }
        return counter.value;
    }

    private static void enforcesCategoryPolicies(Counter counter) {
        Path directory = temporaryDirectory("anilib-updates-category-policy");
        MutableStreamingSource source = new MutableStreamingSource(List.of(episode("episode-1", 1.0d)));
        try (StartedAnilib application = start(directory, source, new RecordingNotifier())) {
            LibraryItem item = new LibraryItem(
                    new LibraryItemId("updates-category-policy"),
                    "Category policy",
                    MediaKind.ANIME,
                    Instant.parse("2026-08-01T00:00:00Z"),
                    Set.of("Seasonal"))
                    .withOrigin(new LibraryOrigin(SOURCE_ID.toString(), SOURCE_ITEM_ID.value()));
            application.capability(LibraryCapabilities.CATALOG).save(item);
            var configuration = application.capability(LibraryCapabilities.CONFIGURATION);
            LibraryDisplayPreferences preferences = LibraryDisplayPreferences.defaults();
            configuration.save(new LibraryConfigurationSnapshot(
                    preferences,
                    List.of(new LibraryCategory(
                            "Seasonal",
                            LibraryDisplayMode.GRID,
                            LibraryDisplayDensity.COMFORTABLE,
                            LibrarySort.TITLE_ASCENDING,
                            LibraryCategoryUpdatePolicy.EXCLUDE))));
            LibraryUpdateService updates = application.capability(UpdateCapabilities.SERVICE);
            updates.runNow().join();
            counter.check(source.invocations() == 0,
                    "an excluded library category must skip source update calls");
            counter.check(updates.snapshot().skippedTitles().size() == 1
                            && updates.snapshot().skippedTitles().getFirst().reason()
                            == LibraryUpdateSkipReason.CATEGORY_EXCLUDED,
                    "skipped update titles must expose their exact policy reason");

            configuration.save(new LibraryConfigurationSnapshot(
                    preferences,
                    List.of(new LibraryCategory(
                            "Seasonal",
                            LibraryDisplayMode.GRID,
                            LibraryDisplayDensity.COMFORTABLE,
                            LibrarySort.TITLE_ASCENDING,
                            LibraryCategoryUpdatePolicy.INCLUDE))));
            updates.configure(new LibraryUpdatePolicy(
                    UpdateInterval.MANUAL,
                    false,
                    false,
                    false,
                    Set.of(),
                    Set.of("Seasonal")));
            updates.runNow().join();
            counter.check(source.invocations() == 1,
                    "an included library category must override the global category exclusion");

            updates.configure(new LibraryUpdatePolicy(
                    UpdateInterval.MANUAL,
                    false,
                    false,
                    false,
                    Set.of(),
                    Set.of(),
                    Set.of(),
                    Set.of(item.id())));
            updates.runNow().join();
            counter.check(source.invocations() == 1
                            && updates.snapshot().skippedTitles().getFirst().reason()
                            == LibraryUpdateSkipReason.TITLE_EXCLUDED,
                    "per-title exclusions must override the general update policy");

            updates.configure(new LibraryUpdatePolicy(
                    UpdateInterval.MANUAL,
                    true,
                    true,
                    true,
                    Set.of(),
                    Set.of("Seasonal"),
                    Set.of(item.id()),
                    Set.of()));
            updates.runNow().join();
            counter.check(source.invocations() == 2 && updates.snapshot().skippedTitles().isEmpty(),
                    "per-title inclusions must override favorite, progress, publication, and category skips");
        } finally {
            deleteDirectory(directory);
        }
    }

    private static void enforcesNetworkPolicy(Counter counter) {
        Path directory = temporaryDirectory("anilib-updates-network-policy");
        MutableStreamingSource source = new MutableStreamingSource(List.of(episode("episode-1", 1.0d)));
        try (StartedAnilib application = start(directory, source, new RecordingNotifier(), () -> false)) {
            LibraryItem item = new LibraryItem(
                    new LibraryItemId("updates-network-policy"),
                    "Network policy",
                    MediaKind.ANIME,
                    Instant.parse("2026-08-01T00:00:00Z"),
                    Set.of())
                    .withFavorite(true)
                    .withOrigin(new LibraryOrigin(SOURCE_ID.toString(), SOURCE_ITEM_ID.value()));
            application.capability(LibraryCapabilities.CATALOG).save(item);
            try {
                application.capability(UpdateCapabilities.SERVICE).runNow().join();
                throw new AssertionError("library update must reject a disallowed network connection");
            } catch (java.util.concurrent.CompletionException expected) {
                counter.check(expected.getCause() instanceof fr.vriege.anilib.feature.updates.LibraryUpdateException,
                        "library update network policy must report a feature-owned failure");
            }
        } finally {
            deleteDirectory(directory);
        }
    }

    private static void cleansOrphanedUpdateState(Counter counter) {
        Path directory = temporaryDirectory("anilib-updates-cleanup");
        MutableStreamingSource source = new MutableStreamingSource(List.of(episode("episode-1", 1.0d)));
        try (StartedAnilib application = start(directory, source, new RecordingNotifier())) {
            LibraryItem item = new LibraryItem(
                    new LibraryItemId("updates-cleanup"),
                    "Update cleanup",
                    MediaKind.ANIME,
                    Instant.parse("2026-08-01T00:00:00Z"),
                    Set.of())
                    .withFavorite(true)
                    .withOrigin(new LibraryOrigin(SOURCE_ID.toString(), SOURCE_ITEM_ID.value()));
            application.capability(LibraryCapabilities.CATALOG).save(item);
            LibraryUpdateService updates = application.capability(UpdateCapabilities.SERVICE);
            updates.runNow().join();
            source.replace(List.of(episode("episode-2", 2.0d), episode("episode-1", 1.0d)));
            updates.runNow().join();
            application.capability(LibraryCapabilities.CATALOG).remove(item.id());
            var result = application.capability(SettingsUiCapabilities.PRESENTATION).cleanUnusedData();
            counter.check(result.removedByOwner().getOrDefault("updates", 0) == 2,
                    "database cleanup must remove update baselines and events for deleted titles");
            counter.check(updates.snapshot().events().isEmpty(),
                    "database cleanup must remove orphaned entries from the Updates feed");
        } finally {
            deleteDirectory(directory);
        }
    }

    private static StartedAnilib start(
            Path directory,
            MutableStreamingSource source,
            LibraryUpdateNotifier notifier) {
        return start(directory, source, notifier, () -> true);
    }

    private static StartedAnilib start(
            Path directory,
            MutableStreamingSource source,
            LibraryUpdateNotifier notifier,
            NetworkStatus networkStatus) {
        return StandardAnilib.start(
                directory,
                new UrlConnectionHttpTransport(),
                PlayerBackends.unavailable(),
                notifier,
                networkStatus,
                List.of(new SourceExtensionPlugin(
                        ComponentDescriptor.of("extension.test-updates", "Test updates", "1.0.0"),
                        source)));
    }

    private static SourceEpisode episode(String identity, double number) {
        return new SourceEpisode(
                new SourceEpisodeId(SOURCE_ITEM_ID, identity),
                "Episode " + number,
                number,
                Optional.of(Instant.parse("2026-08-01T00:00:00Z").plusSeconds((long) number * 3600L)),
                Optional.empty());
    }

    private static Path temporaryDirectory(String prefix) {
        try {
            return Files.createTempDirectory(prefix);
        } catch (IOException exception) {
            throw new AssertionError("Unable to create Updates test directory", exception);
        }
    }

    private static void await(CountDownLatch latch, String message) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new AssertionError(message);
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError(message, exception);
        }
    }

    private static void deleteDirectory(Path directory) {
        if (!Files.exists(directory)) {
            return;
        }
        try (java.util.stream.Stream<Path> entries = Files.walk(directory)) {
            for (Path entry : entries.sorted(java.util.Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(entry);
            }
        } catch (IOException exception) {
            throw new AssertionError("Unable to clean Updates test directory", exception);
        }
    }

    private static final class RecordingNotifier implements LibraryUpdateNotifier {
        private final List<LibraryUpdateNotification> notifications =
                new java.util.concurrent.CopyOnWriteArrayList<>();

        @Override
        public boolean available() {
            return true;
        }

        @Override
        public void publish(LibraryUpdateNotification notification) {
            notifications.add(notification);
        }
    }

    private static final class MutableStreamingSource implements StreamingSource {
        private final AtomicReference<List<SourceEpisode>> episodes;
        private final AtomicInteger invocations = new AtomicInteger();
        private volatile CountDownLatch entered;
        private volatile CountDownLatch release;

        private MutableStreamingSource(List<SourceEpisode> episodes) {
            this.episodes = new AtomicReference<>(List.copyOf(episodes));
        }

        private void replace(List<SourceEpisode> replacement) {
            episodes.set(List.copyOf(replacement));
        }

        private int invocations() {
            return invocations.get();
        }

        private void block(CountDownLatch nextEntered, CountDownLatch nextRelease) {
            entered = nextEntered;
            release = nextRelease;
        }

        private void unblock() {
            entered = null;
            release = null;
        }

        @Override
        public SourceDescriptor descriptor() {
            return new SourceDescriptor(
                    SOURCE_ID,
                    "Test updates",
                    "1.0.0",
                    "en",
                    Set.of(SourceContentKind.ANIME),
                    SourceSdk.API_VERSION);
        }

        @Override
        public List<SourceEpisode> episodes(SourceCatalogueItemId itemId) {
            invocations.incrementAndGet();
            CountDownLatch enteredLatch = entered;
            CountDownLatch releaseLatch = release;
            if (enteredLatch != null && releaseLatch != null) {
                enteredLatch.countDown();
                await(releaseLatch, "test source update was not released");
            }
            return episodes.get();
        }

        @Override
        public List<SourceVideoStream> streams(SourceEpisodeId episodeId) {
            return List.of();
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
