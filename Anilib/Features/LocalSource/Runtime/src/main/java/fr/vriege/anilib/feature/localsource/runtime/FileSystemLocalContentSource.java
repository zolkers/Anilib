package fr.vriege.anilib.feature.localsource.runtime;

import fr.vriege.anilib.feature.localsource.LocalContentSource;
import fr.vriege.anilib.feature.localsource.LocalPage;
import fr.vriege.anilib.feature.localsource.LocalPublication;
import fr.vriege.anilib.feature.localsource.LocalPublicationId;
import fr.vriege.anilib.feature.localsource.LocalPublicationType;
import fr.vriege.anilib.feature.localsource.LocalSeriesMetadata;
import fr.vriege.anilib.feature.localsource.LocalSourceException;
import fr.vriege.anilib.feature.localsource.LocalSourceScan;
import fr.vriege.anilib.feature.source.CatalogueSource;
import fr.vriege.anilib.feature.source.PagedSource;
import fr.vriege.anilib.feature.source.RefreshableSource;
import fr.vriege.anilib.feature.source.SourceBrowseRequest;
import fr.vriege.anilib.feature.source.SourceCatalogueItem;
import fr.vriege.anilib.feature.source.SourceCatalogueItemId;
import fr.vriege.anilib.feature.source.SourceContentUnit;
import fr.vriege.anilib.feature.source.SourceContentUnitId;
import fr.vriege.anilib.feature.source.SourceContentKind;
import fr.vriege.anilib.feature.source.SourceDescriptor;
import fr.vriege.anilib.feature.source.SourceEpisode;
import fr.vriege.anilib.feature.source.SourceEpisodeId;
import fr.vriege.anilib.feature.source.SourceFilterDefinition;
import fr.vriege.anilib.feature.source.SourceFilterType;
import fr.vriege.anilib.feature.source.SourceId;
import fr.vriege.anilib.feature.source.SourcePage;
import fr.vriege.anilib.feature.source.SourcePageResource;
import fr.vriege.anilib.feature.source.SourcePreferenceDefinition;
import fr.vriege.anilib.feature.source.SourcePreferenceType;
import fr.vriege.anilib.feature.source.SourceSdk;
import fr.vriege.anilib.feature.source.SourceSearchRequest;
import fr.vriege.anilib.feature.source.SourceStreamFormat;
import fr.vriege.anilib.feature.source.SourceVideoStream;
import fr.vriege.anilib.feature.source.StreamingSource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class FileSystemLocalContentSource
        implements LocalContentSource, CatalogueSource, PagedSource, StreamingSource, RefreshableSource {
    private static final SourceDescriptor DESCRIPTOR = new SourceDescriptor(
            SourceId.of("anilib.local"),
            "Local library",
            "2.0.0",
            "und",
            Set.of(SourceContentKind.MANGA, SourceContentKind.ANIME),
            SourceSdk.API_VERSION);
    private static final long MAX_PAGE_BYTES = 64L * 1024L * 1024L;
    private static final int MAX_ARCHIVE_ENTRIES = 10_000;
    private static final Set<String> IMAGE_EXTENSIONS =
            Set.of("bmp", "gif", "jpeg", "jpg", "png", "webp");
    private static final Set<String> ARCHIVE_EXTENSIONS = Set.of("cbz", "epub", "zip");
    private static final Comparator<String> NAME_ORDER =
            String.CASE_INSENSITIVE_ORDER.thenComparing(Comparator.naturalOrder());
    private static final List<SourceFilterDefinition> FILTERS = List.of(
            new SourceFilterDefinition(
                    "content",
                    "Content",
                    SourceFilterType.SELECT,
                    List.of("All", "Manga", "Anime"),
                    "All",
                    ""),
            new SourceFilterDefinition(
                    "title",
                    "Title contains",
                    SourceFilterType.TEXT,
                    List.of(),
                    "",
                    ""),
            new SourceFilterDefinition(
                    "sort",
                    "Sort",
                    SourceFilterType.SORT,
                    List.of("Title ascending", "Title descending"),
                    "Title ascending",
                    ""));
    private static final List<SourcePreferenceDefinition> PREFERENCES = List.of(
            new SourcePreferenceDefinition(
                    "include-manga",
                    "Include manga",
                    "Show series from the Aniyomi local folder",
                    SourcePreferenceType.SWITCH,
                    List.of(),
                    "true",
                    false),
            new SourcePreferenceDefinition(
                    "include-anime",
                    "Include anime",
                    "Show series from the Aniyomi localanime folder",
                    SourcePreferenceType.SWITCH,
                    List.of(),
                    "true",
                    false));

    private final Path root;
    private final AniyomiLocalIndex index;
    private volatile AniyomiLocalIndex.Snapshot snapshot;

    public FileSystemLocalContentSource(Path root) {
        this.root = Objects.requireNonNull(root, "root must not be null").toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.root);
        } catch (IOException exception) {
            throw failure("create local source root", exception);
        }
        index = new AniyomiLocalIndex(this.root);
        snapshot = index.scan(1L);
    }

    @Override
    public SourceDescriptor descriptor() {
        return DESCRIPTOR;
    }

    @Override
    public List<LocalPublication> publications() {
        List<LocalPublication> indexed = snapshot.series().stream()
                .map(AniyomiLocalIndex.SeriesEntry::publication)
                .toList();
        try (Stream<Path> entries = Files.list(root)) {
            List<LocalPublication> legacy = entries
                    .filter(Predicate.not(Files::isSymbolicLink))
                    .filter(path -> !path.equals(root.resolve("local")))
                    .filter(path -> !path.equals(root.resolve("localanime")))
                    .filter(path -> Files.isDirectory(path) || isArchive(path))
                    .map(this::publication)
                    .toList();
            return Stream.concat(indexed.stream(), legacy.stream())
                    .sorted(Comparator.comparing(LocalPublication::title, NAME_ORDER))
                    .toList();
        } catch (IOException exception) {
            throw failure("discover local publications", exception);
        }
    }

    @Override
    public Optional<LocalSeriesMetadata> metadata(LocalPublicationId publicationId) {
        Objects.requireNonNull(publicationId, "publicationId must not be null");
        return snapshot.series().stream()
                .filter(value -> value.publication().id().equals(publicationId))
                .map(AniyomiLocalIndex.SeriesEntry::metadata)
                .findFirst();
    }

    @Override
    public LocalSourceScan scan() {
        return snapshot.report();
    }

    @Override
    public synchronized LocalSourceScan rescan() {
        AniyomiLocalIndex.Snapshot replacement = index.scan(snapshot.report().revision() + 1L);
        snapshot = replacement;
        return replacement.report();
    }

    @Override
    public void refresh() {
        rescan();
    }

    @Override
    public SourcePage popular(SourceBrowseRequest request) {
        return cataloguePage(request, "", false);
    }

    @Override
    public boolean supportsLatest() {
        return true;
    }

    @Override
    public SourcePage latest(SourceBrowseRequest request) {
        return cataloguePage(request, "", true);
    }

    @Override
    public SourcePage search(SourceSearchRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        return cataloguePage(request.browseRequest(), request.query(), false);
    }

    @Override
    public List<SourceFilterDefinition> filters() {
        return FILTERS;
    }

    @Override
    public List<SourcePreferenceDefinition> preferences() {
        return PREFERENCES;
    }

    @Override
    public List<SourceContentUnit> contentUnits(SourceCatalogueItemId itemId) {
        LocalPublicationId publicationId = publicationId(itemId);
        resolvePublication(publicationId);
        if (publicationId.type() == LocalPublicationType.MANGA_SERIES) {
            return series(publicationId).chapters().stream()
                    .map(chapter -> new SourceContentUnit(
                            new SourceContentUnitId(itemId, "chapter:" + chapter.relativePath()),
                            chapter.title(),
                            chapter.uploadedAt()))
                    .toList();
        }
        if (publicationId.type() == LocalPublicationType.ANIME_SERIES) {
            return List.of();
        }
        return List.of(new SourceContentUnit(
                new SourceContentUnitId(itemId, "local-content"),
                "Local content",
                Optional.empty()));
    }

    @Override
    public List<SourcePageResource> pages(SourceContentUnitId contentUnitId) {
        Objects.requireNonNull(contentUnitId, "contentUnitId must not be null");
        LocalPublicationId publicationId = publicationId(contentUnitId.itemId());
        if (publicationId.type() == LocalPublicationType.MANGA_SERIES
                && contentUnitId.value().startsWith("chapter:")) {
            String relativePath = contentUnitId.value().substring("chapter:".length());
            AniyomiLocalIndex.ChapterEntry chapter = chapter(publicationId, relativePath);
            return chapterPages(publicationId, chapter).stream()
                    .map(page -> new SourcePageResource(
                            contentUnitId,
                            page.entryName(),
                            page.index(),
                            page.size()))
                    .toList();
        }
        if (!contentUnitId.value().equals("local-content")) {
            throw new LocalSourceException("Unknown local content unit: " + contentUnitId.value());
        }
        return pages(publicationId).stream()
                .map(page -> new SourcePageResource(
                        contentUnitId,
                        page.entryName(),
                        page.index(),
                        page.size()))
                .toList();
    }

    @Override
    public byte[] readPage(SourcePageResource page) {
        Objects.requireNonNull(page, "page must not be null");
        LocalPublicationId publicationId = publicationId(page.contentUnitId().itemId());
        if (publicationId.type() == LocalPublicationType.MANGA_SERIES
                && page.contentUnitId().value().startsWith("chapter:")) {
            String relativePath = page.contentUnitId().value().substring("chapter:".length());
            AniyomiLocalIndex.ChapterEntry chapter = chapter(publicationId, relativePath);
            LocalPage localPage = new LocalPage(
                    publicationId,
                    page.value(),
                    page.index(),
                    page.estimatedBytes());
            LocalPage canonical = chapterPages(publicationId, chapter).stream()
                    .filter(candidate -> candidate.entryName().equals(page.value()))
                    .findFirst()
                    .orElseThrow(() -> new LocalSourceException("Unknown local page: " + page.value()));
            if (!canonical.equals(localPage)) {
                throw new LocalSourceException("Local page metadata does not match the chapter index");
            }
            return readChapterPage(chapter, localPage);
        }
        return read(new LocalPage(publicationId, page.value(), page.index(), page.estimatedBytes()));
    }

    @Override
    public List<LocalPage> pages(LocalPublicationId publicationId) {
        Objects.requireNonNull(publicationId, "publicationId must not be null");
        Path publication = resolvePublication(publicationId);
        return switch (publicationId.type()) {
            case DIRECTORY -> directoryPages(publicationId, publication);
            case ZIP_ARCHIVE -> archivePages(publicationId, publication);
            case MANGA_SERIES -> series(publicationId).chapters().stream()
                    .flatMap(chapter -> chapterPages(publicationId, chapter).stream())
                    .toList();
            case ANIME_SERIES -> List.of();
        };
    }

    @Override
    public byte[] read(LocalPage page) {
        Objects.requireNonNull(page, "page must not be null");
        LocalPage canonical = pages(page.publicationId()).stream()
                .filter(candidate -> candidate.entryName().equals(page.entryName()))
                .findFirst()
                .orElseThrow(() -> new LocalSourceException("Unknown local page: " + page.entryName()));
        if (!canonical.equals(page)) {
            throw new LocalSourceException("Local page metadata does not match the source index");
        }
        Path publication = resolvePublication(page.publicationId());
        return switch (page.publicationId().type()) {
            case DIRECTORY -> readDirectoryPage(publication, page);
            case ZIP_ARCHIVE -> readArchivePage(publication, page);
            case MANGA_SERIES -> readStructuredPage(page);
            case ANIME_SERIES -> throw new LocalSourceException("Anime publications do not contain pages");
        };
    }

    @Override
    public List<SourceEpisode> episodes(SourceCatalogueItemId itemId) {
        LocalPublicationId publicationId = publicationId(itemId);
        if (publicationId.type() != LocalPublicationType.ANIME_SERIES) {
            return List.of();
        }
        return series(publicationId).episodes().stream()
                .map(episode -> new SourceEpisode(
                        new SourceEpisodeId(itemId, "episode:" + episode.relativePath()),
                        episode.title(),
                        episode.number(),
                        episode.uploadedAt(),
                        episode.scanlator(),
                        episode.thumbnail()))
                .toList();
    }

    @Override
    public List<SourceVideoStream> streams(SourceEpisodeId episodeId) {
        Objects.requireNonNull(episodeId, "episodeId must not be null");
        LocalPublicationId publicationId = publicationId(episodeId.itemId());
        if (publicationId.type() != LocalPublicationType.ANIME_SERIES
                || !episodeId.value().startsWith("episode:")) {
            throw new LocalSourceException("Unknown local anime episode: " + episodeId.value());
        }
        String relativePath = episodeId.value().substring("episode:".length());
        AniyomiLocalIndex.EpisodeEntry episode = series(publicationId).episodes().stream()
                .filter(value -> value.relativePath().equals(relativePath))
                .findFirst()
                .orElseThrow(() -> new LocalSourceException("Unknown local anime episode: " + relativePath));
        return List.of(new SourceVideoStream(
                "local",
                "Local file",
                episode.video().toUri(),
                SourceStreamFormat.PROGRESSIVE,
                java.util.Map.of(),
                episode.subtitles()));
    }

    private SourcePage cataloguePage(SourceBrowseRequest request, String query, boolean latest) {
        Objects.requireNonNull(request, "request must not be null");
        java.util.Map<String, String> filters = new java.util.LinkedHashMap<>();
        request.filters().forEach(value -> filters.put(value.filterId(), value.value()));
        String normalizedQuery = query.strip().toLowerCase(Locale.ROOT);
        String titleFilter = filters.getOrDefault("title", "").strip().toLowerCase(Locale.ROOT);
        String content = filters.getOrDefault("content", "All");
        boolean includeManga = Boolean.parseBoolean(
                request.preferences().getOrDefault("include-manga", "true"));
        boolean includeAnime = Boolean.parseBoolean(
                request.preferences().getOrDefault("include-anime", "true"));

        Comparator<LocalPublication> order;
        if (latest) {
            order = Comparator.comparingLong(this::lastModified).reversed()
                    .thenComparing(LocalPublication::title, NAME_ORDER);
        } else if (filters.getOrDefault("sort", "Title ascending").equals("Title descending")) {
            order = Comparator.comparing(LocalPublication::title, NAME_ORDER).reversed();
        } else {
            order = Comparator.comparing(LocalPublication::title, NAME_ORDER);
        }

        List<SourceCatalogueItem> matches = publications().stream()
                .filter(publication -> included(publication, includeManga, includeAnime, content))
                .filter(publication -> contains(publication.title(), normalizedQuery))
                .filter(publication -> contains(publication.title(), titleFilter))
                .sorted(order)
                .map(this::catalogueItem)
                .toList();
        int start = Math.min(matches.size(), Math.multiplyExact(request.page() - 1, request.pageSize()));
        int end = Math.min(matches.size(), start + request.pageSize());
        return new SourcePage(matches.subList(start, end), end < matches.size());
    }

    private SourceCatalogueItem catalogueItem(LocalPublication publication) {
        String key = publication.id().type().name() + ":" + publication.id().relativePath();
        Optional<LocalSeriesMetadata> metadata = metadata(publication.id());
        String description = metadata.map(FileSystemLocalContentSource::description)
                .orElseGet(() -> legacyDescription(publication.id().type()));
        Optional<java.net.URI> thumbnail = snapshot.series().stream()
                .filter(value -> value.publication().id().equals(publication.id()))
                .flatMap(value -> value.cover().stream())
                .findFirst();
        return new SourceCatalogueItem(
                new SourceCatalogueItemId(DESCRIPTOR.id(), key),
                publication.title(),
                description,
                thumbnail,
                publication.id().type() == LocalPublicationType.ANIME_SERIES
                        ? SourceContentKind.ANIME
                        : SourceContentKind.MANGA);
    }

    private static LocalPublicationId publicationId(SourceCatalogueItemId itemId) {
        Objects.requireNonNull(itemId, "itemId must not be null");
        if (!itemId.sourceId().equals(DESCRIPTOR.id())) {
            throw new LocalSourceException("Catalogue item belongs to another source: " + itemId.sourceId());
        }
        int separator = itemId.value().indexOf(':');
        if (separator < 1 || separator == itemId.value().length() - 1) {
            throw new LocalSourceException("Invalid local catalogue identity: " + itemId.value());
        }
        try {
            LocalPublicationType type = LocalPublicationType.valueOf(itemId.value().substring(0, separator));
            return new LocalPublicationId(type, itemId.value().substring(separator + 1));
        } catch (IllegalArgumentException exception) {
            throw new LocalSourceException("Invalid local catalogue identity: " + itemId.value(), exception);
        }
    }

    private long lastModified(LocalPublication publication) {
        try {
            return Files.getLastModifiedTime(root.resolve(publication.id().relativePath())).toMillis();
        } catch (IOException exception) {
            throw failure("read local publication modification time", exception);
        }
    }

    private static boolean included(
            LocalPublication publication,
            boolean includeManga,
            boolean includeAnime,
            String content) {
        boolean anime = publication.id().type() == LocalPublicationType.ANIME_SERIES;
        if ((anime && !includeAnime) || (!anime && !includeManga)) {
            return false;
        }
        return content.equals("All")
                || (content.equals("Manga") && !anime)
                || (content.equals("Anime") && anime);
    }

    private static boolean contains(String title, String query) {
        return query.isEmpty() || title.toLowerCase(Locale.ROOT).contains(query);
    }

    private static String description(LocalSeriesMetadata metadata) {
        List<String> values = new ArrayList<>();
        metadata.author().ifPresent(value -> values.add("Author: " + value));
        metadata.artist().ifPresent(value -> values.add("Artist: " + value));
        if (!metadata.genres().isEmpty()) {
            values.add(String.join(", ", metadata.genres()));
        }
        if (!metadata.description().isBlank()) {
            values.add(metadata.description());
        }
        return values.isEmpty() ? "Local Aniyomi series" : String.join(" · ", values);
    }

    private static String legacyDescription(LocalPublicationType type) {
        return type == LocalPublicationType.DIRECTORY
                ? "Legacy local image folder"
                : "Legacy local ZIP/CBZ archive";
    }

    private LocalPublication publication(Path path) {
        LocalPublicationType type = Files.isDirectory(path)
                ? LocalPublicationType.DIRECTORY
                : LocalPublicationType.ZIP_ARCHIVE;
        String fileName = path.getFileName().toString();
        String title = type == LocalPublicationType.ZIP_ARCHIVE ? withoutExtension(fileName) : fileName;
        return new LocalPublication(new LocalPublicationId(type, fileName), title);
    }

    private List<LocalPage> directoryPages(LocalPublicationId id, Path directory) {
        if (!Files.isDirectory(directory) || Files.isSymbolicLink(directory)) {
            throw new LocalSourceException("Local directory publication is unavailable: " + id.relativePath());
        }
        try (Stream<Path> entries = Files.walk(directory)) {
            List<PageCandidate> candidates = entries
                    .filter(Files::isRegularFile)
                    .filter(Predicate.not(Files::isSymbolicLink))
                    .filter(FileSystemLocalContentSource::isImage)
                    .map(path -> directoryCandidate(directory, path))
                    .sorted(Comparator.comparing(PageCandidate::name, NAME_ORDER))
                    .toList();
            return indexedPages(id, candidates);
        } catch (IOException exception) {
            throw failure("index local directory " + id.relativePath(), exception);
        }
    }

    private AniyomiLocalIndex.SeriesEntry series(LocalPublicationId id) {
        return snapshot.series().stream()
                .filter(value -> value.publication().id().equals(id))
                .findFirst()
                .orElseThrow(() -> new LocalSourceException("Unknown local series: " + id.relativePath()));
    }

    private AniyomiLocalIndex.ChapterEntry chapter(LocalPublicationId id, String relativePath) {
        return series(id).chapters().stream()
                .filter(value -> value.relativePath().equals(relativePath))
                .findFirst()
                .orElseThrow(() -> new LocalSourceException("Unknown local chapter: " + relativePath));
    }

    private List<LocalPage> chapterPages(
            LocalPublicationId publicationId,
            AniyomiLocalIndex.ChapterEntry chapter) {
        return chapter.archive()
                ? archivePages(publicationId, chapter.path())
                : directoryPages(publicationId, chapter.path());
    }

    private byte[] readChapterPage(AniyomiLocalIndex.ChapterEntry chapter, LocalPage page) {
        return chapter.archive()
                ? readArchivePage(chapter.path(), page)
                : readDirectoryPage(chapter.path(), page);
    }

    private byte[] readStructuredPage(LocalPage page) {
        List<AniyomiLocalIndex.ChapterEntry> matches = series(page.publicationId()).chapters().stream()
                .filter(chapter -> chapterPages(page.publicationId(), chapter).contains(page))
                .toList();
        if (matches.size() != 1) {
            throw new LocalSourceException("Structured local page is missing or ambiguous: " + page.entryName());
        }
        return readChapterPage(matches.getFirst(), page);
    }

    private List<LocalPage> archivePages(LocalPublicationId id, Path archive) {
        requireArchive(archive, id);
        try (ZipFile zipFile = new ZipFile(archive.toFile())) {
            if (zipFile.size() > MAX_ARCHIVE_ENTRIES) {
                throw new LocalSourceException(
                        "Local archive contains more than " + MAX_ARCHIVE_ENTRIES + " entries");
            }
            List<PageCandidate> candidates = zipFile.stream()
                    .filter(Predicate.not(ZipEntry::isDirectory))
                    .filter(entry -> isSafeEntry(entry.getName()))
                    .filter(entry -> isImageName(entry.getName()))
                    .map(entry -> new PageCandidate(entry.getName(), entry.getSize()))
                    .sorted(Comparator.comparing(PageCandidate::name, NAME_ORDER))
                    .toList();
            if (new HashSet<>(candidates.stream().map(PageCandidate::name).toList()).size()
                    != candidates.size()) {
                throw new LocalSourceException("Local archive contains duplicate page names");
            }
            return indexedPages(id, candidates);
        } catch (IOException exception) {
            throw failure("index local archive " + id.relativePath(), exception);
        }
    }

    private byte[] readDirectoryPage(Path directory, LocalPage page) {
        Path file = directory.resolve(page.entryName()).normalize();
        if (!file.startsWith(directory) || Files.isSymbolicLink(file) || !Files.isRegularFile(file)) {
            throw new LocalSourceException("Unsafe or missing local page: " + page.entryName());
        }
        try {
            requireReadableSize(Files.size(file), page.entryName());
            try (InputStream input = Files.newInputStream(file)) {
                byte[] bytes = input.readNBytes((int) MAX_PAGE_BYTES + 1);
                if (bytes.length > MAX_PAGE_BYTES) {
                    throw new LocalSourceException("Local page exceeds the size limit: " + page.entryName());
                }
                return bytes;
            }
        } catch (IOException exception) {
            throw failure("read local page " + page.entryName(), exception);
        }
    }

    private byte[] readArchivePage(Path archive, LocalPage page) {
        try (ZipFile zipFile = new ZipFile(archive.toFile())) {
            ZipEntry entry = zipFile.getEntry(page.entryName());
            if (entry == null || entry.isDirectory() || !isSafeEntry(entry.getName())) {
                throw new LocalSourceException("Unsafe or missing archive page: " + page.entryName());
            }
            requireReadableSize(entry.getSize(), page.entryName());
            try (InputStream input = zipFile.getInputStream(entry)) {
                byte[] bytes = input.readNBytes((int) MAX_PAGE_BYTES + 1);
                if (bytes.length > MAX_PAGE_BYTES) {
                    throw new LocalSourceException("Local page exceeds the size limit: " + page.entryName());
                }
                return bytes;
            }
        } catch (IOException exception) {
            throw failure("read archive page " + page.entryName(), exception);
        }
    }

    private Path resolvePublication(LocalPublicationId id) {
        Path resolved = root.resolve(id.relativePath()).normalize();
        if (!resolved.startsWith(root) || Files.isSymbolicLink(resolved)) {
            throw new LocalSourceException("Publication escapes the configured local root");
        }
        return resolved;
    }

    private static PageCandidate directoryCandidate(Path directory, Path file) {
        try {
            String name = directory.relativize(file).toString().replace('\\', '/');
            return new PageCandidate(name, Files.size(file));
        } catch (IOException exception) {
            throw failure("inspect local page " + file, exception);
        }
    }

    private static List<LocalPage> indexedPages(LocalPublicationId id, List<PageCandidate> candidates) {
        List<LocalPage> pages = new ArrayList<>(candidates.size());
        for (int index = 0; index < candidates.size(); index++) {
            PageCandidate candidate = candidates.get(index);
            pages.add(new LocalPage(id, candidate.name(), index, candidate.size()));
        }
        return List.copyOf(pages);
    }

    private static boolean isSafeEntry(String name) {
        try {
            new LocalPage(
                    new LocalPublicationId(LocalPublicationType.ZIP_ARCHIVE, "validation.zip"),
                    name,
                    0,
                    -1);
            return true;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private static boolean isImage(Path path) {
        return isImageName(path.getFileName().toString());
    }

    private static boolean isImageName(String name) {
        return IMAGE_EXTENSIONS.contains(extension(name));
    }

    private static boolean isArchive(Path path) {
        return Files.isRegularFile(path) && ARCHIVE_EXTENSIONS.contains(extension(path));
    }

    private static String extension(Path path) {
        return extension(path.getFileName().toString());
    }

    private static String extension(String name) {
        int separator = name.lastIndexOf('.');
        return separator < 0 ? "" : name.substring(separator + 1).toLowerCase(Locale.ROOT);
    }

    private static String withoutExtension(String name) {
        int separator = name.lastIndexOf('.');
        return separator <= 0 ? name : name.substring(0, separator);
    }

    private static void requireArchive(Path archive, LocalPublicationId id) {
        if (!Files.isRegularFile(archive) || Files.isSymbolicLink(archive) || !isArchive(archive)) {
            throw new LocalSourceException("Local archive publication is unavailable: " + id.relativePath());
        }
    }

    private static void requireReadableSize(long size, String name) {
        if (size > MAX_PAGE_BYTES) {
            throw new LocalSourceException("Local page exceeds the size limit: " + name);
        }
    }

    private static LocalSourceException failure(String operation, IOException cause) {
        return new LocalSourceException("Unable to " + operation, cause);
    }

    private record PageCandidate(String name, long size) {
    }
}
