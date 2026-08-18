package fr.vriege.anilib.feature.localsource.runtime;

import fr.vriege.anilib.feature.localsource.LocalContentSource;
import fr.vriege.anilib.feature.localsource.LocalPage;
import fr.vriege.anilib.feature.localsource.LocalPublication;
import fr.vriege.anilib.feature.localsource.LocalPublicationId;
import fr.vriege.anilib.feature.localsource.LocalPublicationType;
import fr.vriege.anilib.feature.localsource.LocalSourceException;
import fr.vriege.anilib.feature.source.CatalogueSource;
import fr.vriege.anilib.feature.source.PagedSource;
import fr.vriege.anilib.feature.source.SourceBrowseRequest;
import fr.vriege.anilib.feature.source.SourceCatalogueItem;
import fr.vriege.anilib.feature.source.SourceCatalogueItemId;
import fr.vriege.anilib.feature.source.SourceContentUnit;
import fr.vriege.anilib.feature.source.SourceContentUnitId;
import fr.vriege.anilib.feature.source.SourceContentKind;
import fr.vriege.anilib.feature.source.SourceDescriptor;
import fr.vriege.anilib.feature.source.SourceFilterDefinition;
import fr.vriege.anilib.feature.source.SourceFilterType;
import fr.vriege.anilib.feature.source.SourceId;
import fr.vriege.anilib.feature.source.SourcePage;
import fr.vriege.anilib.feature.source.SourcePageResource;
import fr.vriege.anilib.feature.source.SourcePreferenceDefinition;
import fr.vriege.anilib.feature.source.SourcePreferenceType;
import fr.vriege.anilib.feature.source.SourceSdk;
import fr.vriege.anilib.feature.source.SourceSearchRequest;

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

public final class FileSystemLocalContentSource implements LocalContentSource, CatalogueSource, PagedSource {
    private static final SourceDescriptor DESCRIPTOR = new SourceDescriptor(
            SourceId.of("anilib.local"),
            "Local library",
            "1.0.0",
            "und",
            Set.of(SourceContentKind.MANGA),
            SourceSdk.API_VERSION);
    private static final long MAX_PAGE_BYTES = 64L * 1024L * 1024L;
    private static final Set<String> IMAGE_EXTENSIONS =
            Set.of("bmp", "gif", "jpeg", "jpg", "png", "webp");
    private static final Set<String> ARCHIVE_EXTENSIONS = Set.of("cbz", "zip");
    private static final Comparator<String> NAME_ORDER =
            String.CASE_INSENSITIVE_ORDER.thenComparing(Comparator.naturalOrder());
    private static final List<SourceFilterDefinition> FILTERS = List.of(
            new SourceFilterDefinition(
                    "format",
                    "Format",
                    SourceFilterType.SELECT,
                    List.of("All", "Folders", "Archives"),
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
                    "include-folders",
                    "Include folders",
                    "Show image folders in the local catalogue",
                    SourcePreferenceType.SWITCH,
                    List.of(),
                    "true",
                    false),
            new SourcePreferenceDefinition(
                    "include-archives",
                    "Include archives",
                    "Show ZIP and CBZ archives in the local catalogue",
                    SourcePreferenceType.SWITCH,
                    List.of(),
                    "true",
                    false));

    private final Path root;

    public FileSystemLocalContentSource(Path root) {
        this.root = Objects.requireNonNull(root, "root must not be null").toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.root);
        } catch (IOException exception) {
            throw failure("create local source root", exception);
        }
    }

    @Override
    public SourceDescriptor descriptor() {
        return DESCRIPTOR;
    }

    @Override
    public List<LocalPublication> publications() {
        try (Stream<Path> entries = Files.list(root)) {
            return entries
                    .filter(Predicate.not(Files::isSymbolicLink))
                    .filter(path -> Files.isDirectory(path) || isArchive(path))
                    .map(this::publication)
                    .sorted(Comparator.comparing(LocalPublication::title, NAME_ORDER))
                    .toList();
        } catch (IOException exception) {
            throw failure("discover local publications", exception);
        }
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
        return List.of(new SourceContentUnit(
                new SourceContentUnitId(itemId, "local-content"),
                "Local content",
                Optional.empty()));
    }

    @Override
    public List<SourcePageResource> pages(SourceContentUnitId contentUnitId) {
        Objects.requireNonNull(contentUnitId, "contentUnitId must not be null");
        if (!contentUnitId.value().equals("local-content")) {
            throw new LocalSourceException("Unknown local content unit: " + contentUnitId.value());
        }
        LocalPublicationId publicationId = publicationId(contentUnitId.itemId());
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
        return read(new LocalPage(publicationId, page.value(), page.index(), page.estimatedBytes()));
    }

    @Override
    public List<LocalPage> pages(LocalPublicationId publicationId) {
        Objects.requireNonNull(publicationId, "publicationId must not be null");
        Path publication = resolvePublication(publicationId);
        return switch (publicationId.type()) {
            case DIRECTORY -> directoryPages(publicationId, publication);
            case ZIP_ARCHIVE -> archivePages(publicationId, publication);
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
        };
    }

    private SourcePage cataloguePage(SourceBrowseRequest request, String query, boolean latest) {
        Objects.requireNonNull(request, "request must not be null");
        java.util.Map<String, String> filters = new java.util.LinkedHashMap<>();
        request.filters().forEach(value -> filters.put(value.filterId(), value.value()));
        String normalizedQuery = query.strip().toLowerCase(Locale.ROOT);
        String titleFilter = filters.getOrDefault("title", "").strip().toLowerCase(Locale.ROOT);
        String format = filters.getOrDefault("format", "All");
        boolean includeFolders = Boolean.parseBoolean(
                request.preferences().getOrDefault("include-folders", "true"));
        boolean includeArchives = Boolean.parseBoolean(
                request.preferences().getOrDefault("include-archives", "true"));

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
                .filter(publication -> included(publication, includeFolders, includeArchives, format))
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
        String description = publication.id().type() == LocalPublicationType.DIRECTORY
                ? "Local image folder"
                : "Local ZIP/CBZ archive";
        return new SourceCatalogueItem(
                new SourceCatalogueItemId(DESCRIPTOR.id(), key),
                publication.title(),
                description,
                Optional.empty(),
                SourceContentKind.MANGA);
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
            boolean includeFolders,
            boolean includeArchives,
            String format) {
        boolean directory = publication.id().type() == LocalPublicationType.DIRECTORY;
        if ((directory && !includeFolders) || (!directory && !includeArchives)) {
            return false;
        }
        return format.equals("All")
                || (format.equals("Folders") && directory)
                || (format.equals("Archives") && !directory);
    }

    private static boolean contains(String title, String query) {
        return query.isEmpty() || title.toLowerCase(Locale.ROOT).contains(query);
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

    private List<LocalPage> archivePages(LocalPublicationId id, Path archive) {
        requireArchive(archive, id);
        try (ZipFile zipFile = new ZipFile(archive.toFile())) {
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
