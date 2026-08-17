package fr.vriege.anilib.feature.localsource.runtime;

import fr.vriege.anilib.feature.localsource.LocalContentSource;
import fr.vriege.anilib.feature.localsource.LocalPage;
import fr.vriege.anilib.feature.localsource.LocalPublication;
import fr.vriege.anilib.feature.localsource.LocalPublicationId;
import fr.vriege.anilib.feature.localsource.LocalPublicationType;
import fr.vriege.anilib.feature.localsource.LocalSourceException;

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
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** JDK-only local content source for folders and ZIP-compatible archives. */
public final class FileSystemLocalContentSource implements LocalContentSource {
    private static final long MAX_PAGE_BYTES = 64L * 1024L * 1024L;
    private static final Set<String> IMAGE_EXTENSIONS =
            Set.of("bmp", "gif", "jpeg", "jpg", "png", "webp");
    private static final Set<String> ARCHIVE_EXTENSIONS = Set.of("cbz", "zip");
    private static final Comparator<String> NAME_ORDER =
            String.CASE_INSENSITIVE_ORDER.thenComparing(Comparator.naturalOrder());

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
