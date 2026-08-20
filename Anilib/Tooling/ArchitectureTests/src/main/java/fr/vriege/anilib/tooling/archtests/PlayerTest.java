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
import fr.vriege.anilib.feature.player.PlayerBackend;
import fr.vriege.anilib.feature.player.PlayerAdvancedCapability;
import fr.vriege.anilib.feature.player.PlayerAdvancedPlayback;
import fr.vriege.anilib.feature.player.PlayerAdvancedState;
import fr.vriege.anilib.feature.player.PlayerCapabilities;
import fr.vriege.anilib.feature.player.PlayerException;
import fr.vriege.anilib.feature.player.PlayerDecoderPolicy;
import fr.vriege.anilib.feature.player.PlayerMedia;
import fr.vriege.anilib.feature.player.PlayerPlayback;
import fr.vriege.anilib.feature.player.PlayerPlaybackSnapshot;
import fr.vriege.anilib.feature.player.PlayerPlaybackStatus;
import fr.vriege.anilib.feature.player.PlayerPreferences;
import fr.vriege.anilib.feature.player.PlayerQualityPolicy;
import fr.vriege.anilib.feature.player.PlayerService;
import fr.vriege.anilib.feature.player.PlayerSession;
import fr.vriege.anilib.feature.player.PlayerSubtitlePolicy;
import fr.vriege.anilib.feature.player.ui.PlayerController;
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
import fr.vriege.anilib.feature.settings.SettingsCapabilities;
import fr.vriege.anilib.feature.settings.SettingsService;
import fr.vriege.anilib.feature.settings.ui.SettingsUiCapabilities;
import fr.vriege.anilib.foundation.component.ComponentDescriptor;
import fr.vriege.anilib.framework.http.runtime.UrlConnectionHttpTransport;
import fr.vriege.anilib.kernel.AnilibPlugin;
import fr.vriege.anilib.kernel.StartedAnilib;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

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
        migratesLegacySecondProgress(counter);
        completesPlaybackAtConfiguredThreshold(counter);
        verifiesPreferencePolicies(counter);
        verifiesPlaybackBackup(counter);
        suppressesIncognitoPersistence(counter);
        cleansOrphanedPlaybackState(counter);
        rejectsInvalidSourceResults(counter);
        return counter.value;
    }

    private static void migratesLegacySecondProgress(Counter counter) {
        Path directory = temporaryDirectory("anilib-player-legacy-seconds");
        LibraryItem item = animeItem("player-legacy-seconds");
        try {
            try (StartedAnilib application = StandardAnilib.start(
                    directory,
                    List.of(sourcePlugin(new TestStreamingSource(false))))) {
                application.capability(LibraryCapabilities.CATALOG).save(item);
            }
            String state = String.join("\t",
                    "STATE",
                    encoded(item.id().value()),
                    SOURCE_ID.toString(),
                    encoded(SOURCE_ITEM_ID.value()),
                    encoded(FIRST_EPISODE.id().value()),
                    "60",
                    "120000",
                    "false",
                    "2026-08-19T10:00:00Z");
            Files.writeString(
                    directory.resolve("playback-state.anilib"),
                    "ANILIB_PLAYBACK\t1\n" + state + "\n",
                    StandardCharsets.UTF_8);
            try (StartedAnilib restarted = StandardAnilib.start(
                    directory,
                    List.of(sourcePlugin(new TestStreamingSource(false))))) {
                EpisodeSnapshot episode = restarted.capability(PlayerCapabilities.SERVICE)
                        .episodes(item.id()).stream()
                        .filter(snapshot -> snapshot.episode().id().equals(FIRST_EPISODE.id()))
                        .findFirst()
                        .orElseThrow();
                counter.check(episode.playback().orElseThrow().positionMillis() == 60_000L,
                        "legacy platform seconds must migrate to playback milliseconds");
            }
            counter.check(Files.readAllLines(
                            directory.resolve("playback-state.anilib"),
                            StandardCharsets.UTF_8).getFirst().equals("ANILIB_PLAYBACK\t2"),
                    "legacy playback state must be rewritten with the current format");
        } catch (IOException exception) {
            throw new AssertionError("Unable to verify legacy Player progress migration", exception);
        } finally {
            deleteDirectory(directory);
        }
    }

    private static String encoded(String value) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static void completesPlaybackAtConfiguredThreshold(Counter counter) {
        Path directory = temporaryDirectory("anilib-player-completion-threshold");
        try (StartedAnilib application = StandardAnilib.start(
                directory,
                List.of(sourcePlugin(new TestStreamingSource(false))))) {
            LibraryItem item = animeItem("player-completion-threshold");
            application.capability(LibraryCapabilities.CATALOG).save(item);
            PlayerPresentation presentation = application.capability(PlayerUiCapabilities.PRESENTATION);
            try (PlayerController controller = presentation.open(item.id(), FIRST_EPISODE.id())) {
                PlayerPreferences defaults = controller.preferences();
                controller.setPreferences(new PlayerPreferences(
                        defaults.decoderPolicy(),
                        defaults.preferredAudioLanguage(),
                        defaults.subtitlePolicy(),
                        defaults.preferredSubtitleLanguage(),
                        defaults.qualityPolicy(),
                        defaults.preferredQuality(),
                        defaults.introEndMillis(),
                        defaults.outroDurationMillis(),
                        80), false);
                controller.updatePlayback(79_999L, 100_000L);
                counter.check(!controller.snapshot().playback().completed(),
                        "Player must keep progress below the configured threshold unwatched");
                controller.updatePlayback(80_000L, 100_000L);
                counter.check(controller.snapshot().playback().completed(),
                        "Player must mark progress at the configured threshold as watched");
            }
            EpisodeSnapshot episode = presentation.episodes(item.id()).stream()
                    .filter(snapshot -> snapshot.episode().id().equals(FIRST_EPISODE.id()))
                    .findFirst()
                    .orElseThrow();
            counter.check(episode.playback().orElseThrow().completed(),
                    "watched state must remain visible in the episode list");
            EpisodeSnapshot sourceEpisode = presentation.episodes(SOURCE_ITEM_ID).stream()
                    .filter(snapshot -> snapshot.episode().id().equals(FIRST_EPISODE.id()))
                    .findFirst()
                    .orElseThrow();
            counter.check(sourceEpisode.playback().orElseThrow().completed(),
                    "source episode lists must expose playback from their matching Library title");
        } finally {
            deleteDirectory(directory);
        }
    }

    private static void verifiesPreferencePolicies(Counter counter) {
        Path directory = temporaryDirectory("anilib-player-preferences");
        RecordingBackend backend = new RecordingBackend();
        try (StartedAnilib application = StandardAnilib.start(
                directory,
                new UrlConnectionHttpTransport(),
                backend,
                List.of(sourcePlugin(new TestStreamingSource(false))))) {
            LibraryItem item = animeItem("player-preferences");
            application.capability(LibraryCapabilities.CATALOG).save(item);
            PlayerPresentation presentation = application.capability(PlayerUiCapabilities.PRESENTATION);
            PlayerPreferences preferences = new PlayerPreferences(
                    PlayerDecoderPolicy.SOFTWARE,
                    Optional.of("ja"),
                    PlayerSubtitlePolicy.MATCH_LANGUAGE,
                    Optional.of("en"),
                    PlayerQualityPolicy.HIGHEST,
                    Optional.empty(),
                    90_000L,
                    60_000L,
                    85);
            try (PlayerController controller = presentation.open(item.id(), FIRST_EPISODE.id())) {
                controller.setPreferences(preferences, true);
                counter.check(controller.hasPreferenceOverride(),
                        "Player preferences must support per-title overrides");
                counter.check(controller.preferences().equals(preferences),
                        "Player preferences must expose the effective title policy");
                counter.check(controller.snapshot().selectedStreamId().equals("hls-1080"),
                        "highest-quality policy must select the highest numeric quality");
                counter.check(controller.snapshot().selectedSubtitleId().orElseThrow().equals("sub-en"),
                        "subtitle-language policy must select the matching track");
                PlayerMedia media = backend.opened.getLast().media;
                counter.check(media.decoderPolicy() == PlayerDecoderPolicy.SOFTWARE
                                && media.preferredAudioLanguage().orElseThrow().equals("ja"),
                        "decoder and audio policy must reach the platform backend");
                counter.check(controller.advancedCapabilities().equals(Set.of(
                                PlayerAdvancedCapability.LOOP,
                                PlayerAdvancedCapability.RESTART,
                                PlayerAdvancedCapability.FRAME_STEP,
                                PlayerAdvancedCapability.AUDIO_DELAY,
                                PlayerAdvancedCapability.SUBTITLE_DELAY,
                                PlayerAdvancedCapability.ASPECT_RATIO,
                                PlayerAdvancedCapability.DEINTERLACE)),
                        "Player UI must expose only advanced capabilities advertised by the backend");
                controller.setLoop(true);
                controller.setAudioDelay(125L);
                controller.setSubtitleDelay(-250L);
                controller.setAspectRatio(Optional.of("16:9"));
                controller.setDeinterlace(true);
                controller.frameStep();
                PlayerAdvancedState advanced = controller.advancedState().orElseThrow();
                counter.check(advanced.loop()
                                && advanced.audioDelayMillis() == 125L
                                && advanced.subtitleDelayMillis() == -250L
                                && advanced.aspectRatio().orElseThrow().equals("16:9")
                                && advanced.deinterlace(),
                        "advanced Player controls must delegate through the mpv-compatible contract");
            }
            try (PlayerController reopened = presentation.open(item.id(), FIRST_EPISODE.id())) {
                counter.check(reopened.preferences().equals(preferences),
                        "per-title Player preferences must survive a new session");
                reopened.clearPreferenceOverride();
                counter.check(!reopened.hasPreferenceOverride()
                                && reopened.preferences().equals(PlayerPreferences.defaults()),
                        "clearing a title override must restore global Player defaults");
            }
            counter.check(Files.isRegularFile(directory.resolve("player-preferences.properties")),
                    "Player preferences must persist in their owned file");
        } finally {
            deleteDirectory(directory);
        }
    }

    private static void verifiesSelectionAndPersistence(Counter counter) {
        Path directory = temporaryDirectory("anilib-player-state");
        LibraryItemId libraryItemId;
        AtomicInteger notifications = new AtomicInteger();
        RecordingBackend backend = new RecordingBackend();
        try {
            try (StartedAnilib application = StandardAnilib.start(
                    directory,
                    new UrlConnectionHttpTransport(),
                    backend,
                    List.of(sourcePlugin(new TestStreamingSource(false))))) {
                LibraryCatalog library = application.capability(LibraryCapabilities.CATALOG);
                LibraryItem item = animeItem("player-state");
                libraryItemId = item.id();
                library.save(item);
                PlayerService player = application.capability(PlayerCapabilities.SERVICE);
                counter.check(application.capability(PlayerCapabilities.BACKEND) == backend,
                        "Player Bundle must publish the platform-selected media backend");
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
                    counter.check(backend.opened.size() == 1,
                            "opening a Player session must open one platform playback handle");
                    counter.check(backend.opened.getFirst().media.stream().id().equals("hls-1080"),
                            "platform playback must receive the selected source stream");
                    session.play();
                    counter.check(session.playback().snapshot().status() == PlayerPlaybackStatus.PLAYING,
                            "Player commands must delegate through the narrow playback handle");
                    session.seekTo(1_500L);
                    counter.check(session.playback().snapshot().positionMillis() == 1_500L,
                            "Player seeks must remain independent from the platform engine type");
                    counter.check(session.snapshot().streams().size() == 2,
                            "Player must expose every validated stream candidate");
                    counter.check(session.snapshot().selectedStreamId().equals("hls-1080"),
                            "Player must select the source-preferred first stream");
                    session.selectSubtitle(Optional.of("sub-en"));
                    counter.check(session.snapshot().selectedSubtitleId().orElseThrow().equals("sub-en"),
                            "Player must retain a selected subtitle track");
                    session.selectStream("progressive-720");
                    counter.check(backend.opened.size() == 2 && backend.opened.getFirst().closed,
                            "changing streams must replace and close the platform playback handle");
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
                        "opening an episode must create one Library history entry");
                counter.check(saved.history().getFirst().position() == 60_000L,
                        "playback updates must mirror the latest position into Library history");
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
                LibraryItem saved = restarted.capability(LibraryCapabilities.CATALOG)
                        .find(libraryItemId)
                        .orElseThrow();
                counter.check(saved.history().size() == 1,
                        "reopening an episode must update its history row instead of duplicating it");
                counter.check(saved.history().getFirst().position() == 120_000L,
                        "completed playback must update the history resume position");
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

    private static void suppressesIncognitoPersistence(Counter counter) {
        Path directory = temporaryDirectory("anilib-player-incognito");
        try (StartedAnilib application = StandardAnilib.start(
                directory,
                List.of(sourcePlugin(new TestStreamingSource(false))))) {
            LibraryCatalog library = application.capability(LibraryCapabilities.CATALOG);
            LibraryItem item = animeItem("player-incognito");
            library.save(item);
            SettingsService settings = application.capability(SettingsCapabilities.SERVICE);
            settings.replace(settings.snapshot().withIncognitoMode(true));
            PlayerService player = application.capability(PlayerCapabilities.SERVICE);
            try (PlayerSession session = player.open(item.id(), FIRST_EPISODE.id())) {
                session.updatePlayback(30_000L, 120_000L);
            }
            LibraryItem unchanged = library.find(item.id()).orElseThrow();
            counter.check(unchanged.history().isEmpty(),
                    "incognito Player sessions must not append library history");
            counter.check(unchanged.progress().isEmpty(),
                    "incognito Player sessions must not persist playback progress");
            counter.check(player.episodes(item.id()).stream()
                            .filter(episode -> episode.episode().id().equals(FIRST_EPISODE.id()))
                            .findFirst().orElseThrow().playback().isEmpty(),
                    "incognito Player sessions must not write Player-owned resume state");
        } finally {
            deleteDirectory(directory);
        }
    }

    private static void cleansOrphanedPlaybackState(Counter counter) {
        Path directory = temporaryDirectory("anilib-player-cleanup");
        try (StartedAnilib application = StandardAnilib.start(
                directory,
                List.of(sourcePlugin(new TestStreamingSource(false))))) {
            LibraryCatalog library = application.capability(LibraryCapabilities.CATALOG);
            LibraryItem item = animeItem("player-cleanup");
            library.save(item);
            try (PlayerSession session = application.capability(PlayerCapabilities.SERVICE)
                    .open(item.id(), FIRST_EPISODE.id())) {
                session.updatePlayback(10_000L, 100_000L);
            }
            library.remove(item.id());
            var cleanup = application.capability(SettingsUiCapabilities.PRESENTATION).cleanUnusedData();
            counter.check(cleanup.removedByOwner().getOrDefault("player", 0) == 1,
                    "database cleanup must remove Player state whose library title was deleted");
        } finally {
            deleteDirectory(directory);
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

    private static final class RecordingBackend implements PlayerBackend {
        private final List<RecordingPlayback> opened = new ArrayList<>();

        @Override
        public String id() {
            return "recording";
        }

        @Override
        public boolean available() {
            return true;
        }

        @Override
        public PlayerPlayback open(PlayerMedia media) {
            RecordingPlayback playback = new RecordingPlayback(media);
            opened.add(playback);
            return playback;
        }
    }

    private static final class RecordingPlayback implements PlayerPlayback, PlayerAdvancedPlayback {
        private final PlayerMedia media;
        private PlayerPlaybackStatus status = PlayerPlaybackStatus.PAUSED;
        private long position;
        private float volume = 1.0f;
        private float speed = 1.0f;
        private boolean closed;
        private PlayerAdvancedState advanced = PlayerAdvancedState.defaults();

        private RecordingPlayback(PlayerMedia media) {
            this.media = media;
            position = media.startPositionMillis();
        }

        @Override
        public PlayerMedia media() {
            return media;
        }

        @Override
        public PlayerPlaybackSnapshot snapshot() {
            return new PlayerPlaybackSnapshot(
                    status,
                    position,
                    -1L,
                    volume,
                    speed,
                    Optional.empty());
        }

        @Override
        public void play() {
            status = PlayerPlaybackStatus.PLAYING;
        }

        @Override
        public void pause() {
            status = PlayerPlaybackStatus.PAUSED;
        }

        @Override
        public void seekTo(long positionMillis) {
            position = positionMillis;
        }

        @Override
        public void setVolume(float value) {
            volume = value;
        }

        @Override
        public void setPlaybackSpeed(float value) {
            speed = value;
        }

        @Override
        public void selectSubtitle(Optional<String> subtitleId) {
            // The Player session validates ownership before delegation.
        }

        @Override
        public Set<PlayerAdvancedCapability> advancedCapabilities() {
            return Set.of(PlayerAdvancedCapability.values());
        }

        @Override
        public PlayerAdvancedState advancedState() {
            return advanced;
        }

        @Override
        public void setLoop(boolean loop) {
            advanced = new PlayerAdvancedState(
                    loop,
                    advanced.audioDelayMillis(),
                    advanced.subtitleDelayMillis(),
                    advanced.aspectRatio(),
                    advanced.deinterlace());
        }

        @Override
        public void restart() {
            position = 0L;
            status = PlayerPlaybackStatus.PLAYING;
        }

        @Override
        public void frameStep() {
            position += 40L;
            status = PlayerPlaybackStatus.PAUSED;
        }

        @Override
        public void setAudioDelay(long delayMillis) {
            advanced = new PlayerAdvancedState(
                    advanced.loop(),
                    delayMillis,
                    advanced.subtitleDelayMillis(),
                    advanced.aspectRatio(),
                    advanced.deinterlace());
        }

        @Override
        public void setSubtitleDelay(long delayMillis) {
            advanced = new PlayerAdvancedState(
                    advanced.loop(),
                    advanced.audioDelayMillis(),
                    delayMillis,
                    advanced.aspectRatio(),
                    advanced.deinterlace());
        }

        @Override
        public void setAspectRatio(Optional<String> aspectRatio) {
            advanced = new PlayerAdvancedState(
                    advanced.loop(),
                    advanced.audioDelayMillis(),
                    advanced.subtitleDelayMillis(),
                    aspectRatio,
                    advanced.deinterlace());
        }

        @Override
        public void setDeinterlace(boolean enabled) {
            advanced = new PlayerAdvancedState(
                    advanced.loop(),
                    advanced.audioDelayMillis(),
                    advanced.subtitleDelayMillis(),
                    advanced.aspectRatio(),
                    enabled);
        }

        @Override
        public void close() {
            closed = true;
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
