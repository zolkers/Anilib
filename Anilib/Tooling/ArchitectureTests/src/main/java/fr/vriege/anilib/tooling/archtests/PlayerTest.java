package fr.vriege.anilib.tooling.archtests;

import fr.vriege.anilib.configuration.standard.StandardAnilib;
import fr.vriege.anilib.feature.backup.BackupCapabilities;
import fr.vriege.anilib.feature.backup.BackupService;
import fr.vriege.anilib.feature.library.LibraryCapabilities;
import fr.vriege.anilib.feature.library.LibraryCatalog;
import fr.vriege.anilib.feature.library.LibraryItem;
import fr.vriege.anilib.feature.library.LibraryItemId;
import fr.vriege.anilib.feature.library.LibraryOrigin;
import fr.vriege.anilib.feature.library.MediaKind;
import fr.vriege.anilib.feature.player.EpisodeSnapshot;
import fr.vriege.anilib.feature.player.PlayerCapabilities;
import fr.vriege.anilib.feature.player.PlayerException;
import fr.vriege.anilib.feature.player.PlayerService;
import fr.vriege.anilib.feature.player.PlayerSession;
import fr.vriege.anilib.feature.player.ui.PlayerPresentation;
import fr.vriege.anilib.feature.player.ui.PlayerUiCapabilities;
import fr.vriege.anilib.feature.source.SourceCatalogueItemId;
import fr.vriege.anilib.feature.source.SourceContentKind;
import fr.vriege.anilib.feature.source.SourceDescriptor;
import fr.vriege.anilib.feature.source.SourceEpisode;
import fr.vriege.anilib.feature.source.SourceEpisodeId;
import fr.vriege.anilib.feature.source.SourceId;
import fr.vriege.anilib.feature.source.SourceSdk;
import fr.vriege.anilib.feature.source.SourceStreamFormat;
import fr.vriege.anilib.feature.source.SourceSubtitleTrack;
import fr.vriege.anilib.feature.source.SourceVideoStream;
import fr.vriege.anilib.feature.source.StreamingSource;
import fr.vriege.anilib.feature.source.SourceExtensionPlugin;
import fr.vriege.anilib.foundation.component.ComponentDescriptor;
import fr.vriege.anilib.kernel.AnilibPlugin;
import fr.vriege.anilib.kernel.StartedAnilib;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/** Episode, stream, subtitle, persistence, backup, and validation checks. */
final class PlayerTest {
    private static final SourceId SOURCE_ID = SourceId.of("test.streaming");
    private static final SourceCatalogueItemId SOURCE_ITEM_ID =
            new SourceCatalogueItemId(SOURCE_ID, "anime-title");
    private static final SourceEpisode FIRST_EPISODE = new SourceEpisode(
            new SourceEpisodeId(SOURCE_ITEM_ID, "episode-1"),
            "A new beginning",
            1.0d,
            Optional.of(Instant.parse("2026-08-01T12:00:00Z")),
            Optional.of("Test group"));
    private static final SourceEpisode SECOND_EPISODE = new SourceEpisode(
            new SourceEpisodeId(SOURCE_ITEM_ID, "episode-2"),
            "The next step",
            2.0d,
            Optional.of(Instant.parse("2026-08-08T12:00:00Z")),
            Optional.empty());

    private PlayerTest() {
    }

    static int run() {
        Counter counter = new Counter();
        verifiesSelectionAndPersistence(counter);
        verifiesPlaybackBackup(counter);
        rejectsInvalidSourceResults(counter);
        return counter.value;
    }

    private static void verifiesSelectionAndPersistence(Counter counter) {
        Path directory = temporaryDirectory("anilib-player-state");
        LibraryItemId libraryItemId;
        AtomicInteger notifications = new AtomicInteger();
        try {
            try (StartedAnilib application = StandardAnilib.start(
                    directory,
                    List.of(sourcePlugin(new TestStreamingSource(false))))) {
                LibraryCatalog library = application.capability(LibraryCapabilities.CATALOG);
                LibraryItem item = animeItem("player-state");
                libraryItemId = item.id();
                library.save(item);
                PlayerService player = application.capability(PlayerCapabilities.SERVICE);
                PlayerPresentation presentation = application.capability(PlayerUiCapabilities.PRESENTATION);
                counter.check(player.canOpen(item.id()),
                        "Player must recognize anime from a streaming source");
                counter.check(presentation.canOpen(item.id()),
                        "shared Player presentation must expose playable titles");
                List<EpisodeSnapshot> episodes = player.episodes(item.id());
                counter.check(episodes.stream().map(snapshot -> snapshot.episode().id()).toList()
                                .equals(List.of(SECOND_EPISODE.id(), FIRST_EPISODE.id())),
                        "Player must preserve the source episode order");
                counter.check(episodes.stream().allMatch(snapshot -> snapshot.playback().isEmpty()),
                        "new episodes must not fabricate playback state");
                AutoCloseable registration = player.observe(notifications::incrementAndGet);
                try (PlayerSession session = player.open(item.id(), FIRST_EPISODE.id())) {
                    counter.check(session.snapshot().streams().size() == 2,
                            "Player must expose every validated stream candidate");
                    counter.check(session.snapshot().selectedStreamId().equals("hls-1080"),
                            "Player must select the source-preferred first stream");
                    session.selectSubtitle(Optional.of("sub-en"));
                    counter.check(session.snapshot().selectedSubtitleId().orElseThrow().equals("sub-en"),
                            "Player must retain a selected subtitle track");
                    session.selectStream("progressive-720");
                    counter.check(session.snapshot().selectedSubtitleId().isEmpty(),
                            "changing streams must clear an unavailable subtitle");
                    expectPlayerFailure(
                            () -> session.selectSubtitle(Optional.of("sub-en")),
                            counter,
                            "Player must reject subtitles from another stream");
                    session.updatePlayback(60_000L, 120_000L);
                    counter.check(session.snapshot().playback().completion().orElseThrow() == 0.5d,
                            "Player must expose the persisted completion ratio");
                    counter.check(notifications.get() == 1,
                            "playback updates must notify shared presentation observers");
                } finally {
                    registration.close();
                }
                LibraryItem saved = library.find(item.id()).orElseThrow();
                counter.check(saved.progress().orElseThrow().position() == 60_000L,
                        "playback updates must mirror resume position into Library");
                counter.check(saved.history().size() == 1,
                        "opening an episode must append one Library history entry");
            }

            try (StartedAnilib restarted = StandardAnilib.start(
                    directory,
                    List.of(sourcePlugin(new TestStreamingSource(false))))) {
                PlayerService player = restarted.capability(PlayerCapabilities.SERVICE);
                EpisodeSnapshot episode = player.episodes(libraryItemId).stream()
                        .filter(snapshot -> snapshot.episode().id().equals(FIRST_EPISODE.id()))
                        .findFirst()
                        .orElseThrow();
                counter.check(episode.playback().orElseThrow().positionMillis() == 60_000L,
                        "episode resume state must survive a product restart");
                PlayerSession session = player.open(libraryItemId, FIRST_EPISODE.id());
                session.markCompleted();
                counter.check(session.snapshot().playback().completed(),
                        "mark completed must persist the watched state");
                counter.check(session.snapshot().playback().positionMillis() == 120_000L,
                        "mark completed must advance known playback to its duration");
                session.close();
                expectPlayerFailure(session::snapshot, counter,
                        "closed Player sessions must reject further access");
            }
            counter.check(Files.isRegularFile(directory.resolve("playback-state.anilib")),
                    "Player must persist resume state in its owned file");
        } catch (Exception exception) {
            throw new AssertionError("Unable to run Player persistence test", exception);
        } finally {
            deleteDirectory(directory);
        }
    }

    private static void verifiesPlaybackBackup(Counter counter) {
        Path sourceDirectory = temporaryDirectory("anilib-player-backup-source");
        Path targetDirectory = temporaryDirectory("anilib-player-backup-target");
        Path backupPath;
        LibraryItem item = animeItem("player-backup");
        try {
            try (StartedAnilib source = StandardAnilib.start(
                    sourceDirectory,
                    List.of(sourcePlugin(new TestStreamingSource(false))))) {
                source.capability(LibraryCapabilities.CATALOG).save(item);
                try (PlayerSession session = source.capability(PlayerCapabilities.SERVICE)
                        .open(item.id(), SECOND_EPISODE.id())) {
                    session.updatePlayback(90_000L, 180_000L);
                }
                BackupService backups = source.capability(BackupCapabilities.SERVICE);
                backupPath = backups.createBackup().path();
                counter.check(backups.inspect(backupPath).sections().stream()
                                .anyMatch(section -> section.id().value().equals("playback-state")
                                        && section.entryCount() == 1),
                        "standard backups must include Player-owned playback state");
            }

            try (StartedAnilib target = StandardAnilib.start(
                    targetDirectory,
                    List.of(sourcePlugin(new TestStreamingSource(false))))) {
                target.capability(BackupCapabilities.SERVICE).restore(backupPath);
                EpisodeSnapshot restored = target.capability(PlayerCapabilities.SERVICE)
                        .episodes(item.id()).stream()
                        .filter(snapshot -> snapshot.episode().id().equals(SECOND_EPISODE.id()))
                        .findFirst()
                        .orElseThrow();
                counter.check(restored.playback().orElseThrow().positionMillis() == 90_000L,
                        "Player backup codec must restore per-episode resume state");
            }
        } finally {
            deleteDirectory(sourceDirectory);
            deleteDirectory(targetDirectory);
        }
    }

    private static void rejectsInvalidSourceResults(Counter counter) {
        Path directory = temporaryDirectory("anilib-player-invalid");
        try (StartedAnilib application = StandardAnilib.start(
                directory,
                List.of(sourcePlugin(new TestStreamingSource(true))))) {
            LibraryItem item = animeItem("player-invalid");
            application.capability(LibraryCapabilities.CATALOG).save(item);
            PlayerService player = application.capability(PlayerCapabilities.SERVICE);
            expectPlayerFailure(
                    () -> player.open(item.id(), FIRST_EPISODE.id()),
                    counter,
                    "Player must reject duplicate stream identities");
            LibraryItem manga = LibraryItem.create("Not anime", MediaKind.MANGA)
                    .withOrigin(new LibraryOrigin(SOURCE_ID.toString(), SOURCE_ITEM_ID.value()));
            application.capability(LibraryCapabilities.CATALOG).save(manga);
            counter.check(!player.canOpen(manga.id()),
                    "Player must not claim manga titles from a streaming source");
        } finally {
            deleteDirectory(directory);
        }
    }

    private static LibraryItem animeItem(String id) {
        return new LibraryItem(
                new LibraryItemId(id),
                "Streaming title",
                MediaKind.ANIME,
                Instant.parse("2026-08-01T10:00:00Z"),
                Set.of())
                .withOrigin(new LibraryOrigin(SOURCE_ID.toString(), SOURCE_ITEM_ID.value()));
    }

    private static AnilibPlugin sourcePlugin(StreamingSource source) {
        return new SourceExtensionPlugin(
                ComponentDescriptor.of("extension.test-streaming", "Test streaming", "1.0.0"),
                source);
    }

    private static Path temporaryDirectory(String prefix) {
        try {
            return Files.createTempDirectory(prefix);
        } catch (IOException exception) {
            throw new AssertionError("Unable to create Player test directory", exception);
        }
    }

    private static void expectPlayerFailure(Runnable action, Counter counter, String message) {
        try {
            action.run();
            throw new AssertionError(message);
        } catch (PlayerException expected) {
            counter.value++;
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
            throw new AssertionError("Unable to clean Player test directory", exception);
        }
    }

    private static final class TestStreamingSource implements StreamingSource {
        private final boolean duplicateStreams;

        private TestStreamingSource(boolean duplicateStreams) {
            this.duplicateStreams = duplicateStreams;
        }

        @Override
        public SourceDescriptor descriptor() {
            return new SourceDescriptor(
                    SOURCE_ID,
                    "Test streaming",
                    "1.0.0",
                    "en",
                    Set.of(SourceContentKind.ANIME),
                    SourceSdk.API_VERSION);
        }

        @Override
        public List<SourceEpisode> episodes(SourceCatalogueItemId itemId) {
            return List.of(SECOND_EPISODE, FIRST_EPISODE);
        }

        @Override
        public List<SourceVideoStream> streams(SourceEpisodeId episodeId) {
            SourceSubtitleTrack english = new SourceSubtitleTrack(
                    "sub-en",
                    "English",
                    Optional.of("en"),
                    URI.create("https://media.example.test/subtitles/en.vtt"),
                    Map.of("Referer", "https://media.example.test"));
            SourceVideoStream hls = new SourceVideoStream(
                    "hls-1080",
                    "1080p",
                    URI.create("https://media.example.test/video/master.m3u8"),
                    SourceStreamFormat.HLS,
                    Map.of("Referer", "https://media.example.test"),
                    List.of(english));
            SourceVideoStream progressive = new SourceVideoStream(
                    duplicateStreams ? "hls-1080" : "progressive-720",
                    "720p",
                    URI.create("https://media.example.test/video/episode.mp4"),
                    SourceStreamFormat.PROGRESSIVE,
                    Map.of(),
                    List.of());
            return List.of(hls, progressive);
        }
    }

    private static final class Counter {
        private int value;

        private void check(boolean condition, String message) {
            if (!condition) {
                throw new AssertionError(message);
            }
            value++;
        }
    }
}
