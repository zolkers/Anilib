package fr.vriege.anilib.tooling.archtests;

import fr.vriege.anilib.feature.localsource.LocalContentSource;
import fr.vriege.anilib.feature.localsource.LocalPublication;
import fr.vriege.anilib.feature.localsource.LocalPublicationId;
import fr.vriege.anilib.feature.localsource.LocalPublicationType;
import fr.vriege.anilib.feature.localsource.LocalSeriesStatus;
import fr.vriege.anilib.feature.localsource.LocalSourceException;
import fr.vriege.anilib.feature.localsource.runtime.FileSystemLocalContentSource;
import fr.vriege.anilib.feature.source.PagedSource;
import fr.vriege.anilib.feature.source.SourceBrowseRequest;
import fr.vriege.anilib.feature.source.SourceCatalogueItem;
import fr.vriege.anilib.feature.source.SourceContentKind;
import fr.vriege.anilib.feature.source.SourceContentUnit;
import fr.vriege.anilib.feature.source.SourceEpisode;
import fr.vriege.anilib.feature.source.SourcePageResource;
import fr.vriege.anilib.feature.source.StreamingSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import java.util.Arrays;
import java.util.stream.Stream;

final class LocalSourceTest {
    private static final byte[] FIRST_IMAGE = {1, 2, 3};
    private static final byte[] SECOND_IMAGE = {4, 5, 6, 7};

    private LocalSourceTest() {
    }

    static int run() {
        Counter counter = new Counter();
        Path root = null;
        try {
            root = Files.createTempDirectory("anilib-local-source");
            prepareManga(root);
            prepareAnime(root);
            verifySource(root, counter);
        } catch (IOException exception) {
            throw new AssertionError("Local source test failed", exception);
        } finally {
            if (root != null) {
                try {
                    deleteTree(root);
                } catch (IOException exception) {
                    throw new AssertionError("Unable to clean local source test", exception);
                }
            }
        }
        rejectsTraversal(counter);
        rejectsArchiveEntryFlood(counter);
        return counter.value;
    }

    private static void verifySource(Path root, Counter counter) throws IOException {
        FileSystemLocalContentSource source = new FileSystemLocalContentSource(root);
        List<LocalPublication> publications = source.publications();
        counter.check(publications.size() == 2,
                "Aniyomi local and localanime folders must each expose their series");
        LocalPublication manga = publication(publications, LocalPublicationType.MANGA_SERIES);
        LocalPublication anime = publication(publications, LocalPublicationType.ANIME_SERIES);
        counter.check(manga.title().equals("Configured Manga") && anime.title().equals("Configured Anime"),
                "details.json titles must replace folder names");
        counter.check(source.metadata(manga.id()).orElseThrow().status() == LocalSeriesStatus.COMPLETED,
                "details.json metadata must retain the Aniyomi publication status");
        counter.check(source.scan().mangaSeries() == 1 && source.scan().animeSeries() == 1,
                "the scan report must count both local media kinds");

        List<SourceCatalogueItem> catalogue = source.popular(new SourceBrowseRequest(
                1,
                30,
                List.of(),
                Map.of("include-manga", "true", "include-anime", "true"))).items();
        SourceCatalogueItem mangaItem = item(catalogue, SourceContentKind.MANGA);
        SourceCatalogueItem animeItem = item(catalogue, SourceContentKind.ANIME);
        counter.check(mangaItem.thumbnail().isPresent() && animeItem.thumbnail().isPresent(),
                "cover.jpg must be exposed as the catalogue thumbnail");
        counter.check(mangaItem.description().contains("Manga author"),
                "local catalogue descriptions must include series metadata");

        verifyManga(source, mangaItem, counter);
        verifyAnime(source, animeItem, root, counter);
    }

    private static void verifyManga(
            PagedSource source,
            SourceCatalogueItem manga,
            Counter counter) {
        List<SourceContentUnit> chapters = source.contentUnits(manga.id());
        counter.check(chapters.stream().map(SourceContentUnit::title).toList()
                        .equals(List.of("Chapter two", "Chapter ten")),
                "chapters.json and numeric chapter ordering must be applied");
        counter.check(chapters.getFirst().publishedAt().isPresent(),
                "chapter upload dates must be parsed from chapters.json");
        List<SourcePageResource> archivePages = source.pages(chapters.getFirst().id());
        counter.check(archivePages.stream().map(SourcePageResource::value).toList()
                        .equals(List.of("002.jpg", "010.png")),
                "CBZ pages must be naturally indexed inside a structured manga chapter");
        counter.check(Arrays.equals(source.readPage(archivePages.getFirst()), FIRST_IMAGE),
                "structured manga chapter bytes must be readable");
    }

    private static void verifyAnime(
            FileSystemLocalContentSource source,
            SourceCatalogueItem anime,
            Path root,
            Counter counter) throws IOException {
        StreamingSource streaming = source;
        List<SourceEpisode> episodes = streaming.episodes(anime.id());
        counter.check(episodes.stream().map(SourceEpisode::title).toList()
                        .equals(List.of("First episode", "Episode ten")),
                "episodes.json and numeric video ordering must be applied");
        counter.check(episodes.getFirst().thumbnail().isPresent(),
                "a matching local image must be exposed as the episode thumbnail");
        var streams = streaming.streams(episodes.getFirst().id());
        counter.check(streams.size() == 1 && streams.getFirst().location().getScheme().equals("file"),
                "local videos must resolve to a progressive file stream");
        counter.check(streams.getFirst().subtitles().size() == 1
                        && streams.getFirst().subtitles().getFirst().language().orElseThrow().equals("en"),
                "sidecar subtitles must be matched to the video and retain their language tag");

        Path animeDirectory = root.resolve("localanime").resolve("Anime Folder");
        Files.write(animeDirectory.resolve("ep02.mp4"), new byte[]{3});
        counter.check(streaming.episodes(anime.id()).size() == 2,
                "the durable local index must change only after an explicit rescan");
        long revision = source.scan().revision();
        counter.check(source.rescan().revision() == revision + 1
                        && streaming.episodes(anime.id()).size() == 3,
                "rescan must atomically refresh newly discovered local videos");
    }

    private static LocalPublication publication(
            List<LocalPublication> publications,
            LocalPublicationType type) {
        return publications.stream()
                .filter(publication -> publication.id().type() == type)
                .findFirst()
                .orElseThrow();
    }

    private static SourceCatalogueItem item(
            List<SourceCatalogueItem> catalogue,
            SourceContentKind kind) {
        return catalogue.stream()
                .filter(item -> item.contentKind() == kind)
                .findFirst()
                .orElseThrow();
    }

    private static void prepareManga(Path root) throws IOException {
        Path series = Files.createDirectories(root.resolve("local").resolve("Manga Folder"));
        Files.write(series.resolve("cover.jpg"), FIRST_IMAGE);
        Files.writeString(series.resolve("details.json"), """
                {
                  "title": "Configured Manga",
                  "author": "Manga author",
                  "artist": "Manga artist",
                  "description": "Local manga description",
                  "genre": ["Action", "Drama"],
                  "status": "2"
                }
                """);
        Files.writeString(series.resolve("chapters.json"), """
                [
                  {"chapter_number": 10, "name": "Chapter ten"},
                  {"chapter_number": 2, "name": "Chapter two", "date_upload": "2024-01-02T03:04:05"}
                ]
                """);
        Path chapter = Files.createDirectories(series.resolve("chapter_10"));
        Files.write(chapter.resolve("001.jpg"), SECOND_IMAGE);
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(series.resolve("chapter_2.cbz")))) {
            writeEntry(output, "010.png", SECOND_IMAGE);
            writeEntry(output, "002.jpg", FIRST_IMAGE);
        }
    }

    private static void prepareAnime(Path root) throws IOException {
        Path series = Files.createDirectories(root.resolve("localanime").resolve("Anime Folder"));
        Files.write(series.resolve("cover.jpg"), FIRST_IMAGE);
        Files.write(series.resolve("ep01.jpg"), SECOND_IMAGE);
        Files.write(series.resolve("ep01.mp4"), new byte[]{1});
        Files.write(series.resolve("ep10.mkv"), new byte[]{2});
        Files.writeString(series.resolve("ep01.en.srt"), "1\n00:00:00,000 --> 00:00:01,000\nHello");
        Files.writeString(series.resolve("details.json"), """
                {"title": "Configured Anime", "description": "Local anime", "status": 1}
                """);
        Files.writeString(series.resolve("episodes.json"), """
                [
                  {"episode_number": 10, "name": "Episode ten"},
                  {"episode_number": 1, "name": "First episode", "scanlator": "Local group"}
                ]
                """);
    }

    private static void writeEntry(ZipOutputStream output, String name, byte[] bytes) throws IOException {
        output.putNextEntry(new ZipEntry(name));
        output.write(bytes);
        output.closeEntry();
    }

    private static void rejectsTraversal(Counter counter) {
        try {
            new LocalPublicationId(LocalPublicationType.MANGA_SERIES, "../escape");
            throw new AssertionError("Expected traversal identity rejection");
        } catch (IllegalArgumentException expected) {
            counter.value++;
        }
    }

    private static void rejectsArchiveEntryFlood(Counter counter) {
        Path root = null;
        try {
            root = Files.createTempDirectory("anilib-local-archive-limit");
            Path series = Files.createDirectories(root.resolve("local").resolve("Archive flood"));
            Path archive = series.resolve("chapter.cbz");
            try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(archive))) {
                for (int index = 0; index <= 10_000; index++) {
                    writeEntry(output, String.format("%05d.jpg", index), new byte[0]);
                }
            }
            FileSystemLocalContentSource source = new FileSystemLocalContentSource(root);
            SourceCatalogueItem item = source.popular(new SourceBrowseRequest(
                    1,
                    30,
                    List.of(),
                    Map.of("include-manga", "true", "include-anime", "true"))).items().getFirst();
            SourceContentUnit chapter = source.contentUnits(item.id()).getFirst();
            try {
                source.pages(chapter.id());
                throw new AssertionError("Expected archive entry limit rejection");
            } catch (LocalSourceException expected) {
                counter.value++;
            }
        } catch (IOException exception) {
            throw new AssertionError("Unable to test local archive entry limit", exception);
        } finally {
            if (root != null) {
                try {
                    deleteTree(root);
                } catch (IOException exception) {
                    throw new AssertionError("Unable to clean archive limit test", exception);
                }
            }
        }
    }

    private static void deleteTree(Path root) throws IOException {
        try (Stream<Path> paths = Files.walk(root)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
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

        private void expectSourceFailure(Runnable action, String message) {
            try {
                action.run();
                throw new AssertionError(message);
            } catch (LocalSourceException expected) {
                value++;
            }
        }
    }
}
