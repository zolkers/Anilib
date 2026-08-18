package fr.vriege.anilib.feature.localsource.runtime;

import fr.vriege.anilib.feature.localsource.LocalPublication;
import fr.vriege.anilib.feature.localsource.LocalPublicationId;
import fr.vriege.anilib.feature.localsource.LocalPublicationType;
import fr.vriege.anilib.feature.localsource.LocalSeriesMetadata;
import fr.vriege.anilib.feature.localsource.LocalSeriesStatus;
import fr.vriege.anilib.feature.localsource.LocalSourceException;
import fr.vriege.anilib.feature.localsource.LocalSourceScan;
import fr.vriege.anilib.feature.source.SourceSubtitleTrack;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

final class AniyomiLocalIndex {
    private static final long MAX_JSON_BYTES = 2L * 1024L * 1024L;
    private static final int MAX_SERIES = 100_000;
    private static final int MAX_UNITS_PER_SERIES = 100_000;
    private static final Pattern NUMBER = Pattern.compile("(?<![0-9])(\\d+(?:\\.\\d+)?)");
    private static final Set<String> IMAGES = Set.of("bmp", "gif", "jpeg", "jpg", "png", "webp");
    private static final Set<String> ARCHIVES = Set.of("cbz", "epub", "zip");
    private static final Set<String> VIDEOS = Set.of("mkv", "mp4");
    private static final Set<String> SUBTITLES = Set.of("ass", "ssa", "srt", "vtt");
    private static final Comparator<String> NAME_ORDER =
            String.CASE_INSENSITIVE_ORDER.thenComparing(Comparator.naturalOrder());
    private static final Comparator<UnitEntry> UNIT_ORDER = Comparator
            .comparingDouble((UnitEntry value) -> value.number() < 0 ? Double.MAX_VALUE : value.number())
            .thenComparing(UnitEntry::title, NAME_ORDER)
            .thenComparing(UnitEntry::relativePath, NAME_ORDER);

    private final Path root;
    private final Path mangaRoot;
    private final Path animeRoot;

    AniyomiLocalIndex(Path root) {
        this.root = root;
        mangaRoot = root.resolve("local");
        animeRoot = root.resolve("localanime");
    }

    Snapshot scan(long revision) {
        try {
            Files.createDirectories(mangaRoot);
            Files.createDirectories(animeRoot);
            List<SeriesEntry> entries = new ArrayList<>();
            entries.addAll(scanManga());
            entries.addAll(scanAnime());
            if (entries.size() > MAX_SERIES) {
                throw new LocalSourceException("Local source exceeds the series limit of " + MAX_SERIES);
            }
            entries.sort(Comparator.comparing(value -> value.publication().title(), NAME_ORDER));
            int chapters = entries.stream().mapToInt(value -> value.chapters().size()).sum();
            int episodes = entries.stream().mapToInt(value -> value.episodes().size()).sum();
            int manga = (int) entries.stream().filter(SeriesEntry::manga).count();
            LocalSourceScan report = new LocalSourceScan(
                    revision,
                    Instant.now(),
                    manga,
                    entries.size() - manga,
                    chapters,
                    episodes);
            return new Snapshot(report, List.copyOf(entries));
        } catch (IOException exception) {
            throw new LocalSourceException("Unable to scan Aniyomi local folders", exception);
        }
    }

    private List<SeriesEntry> scanManga() throws IOException {
        try (Stream<Path> paths = Files.list(mangaRoot)) {
            return paths.filter(Predicate.not(Files::isSymbolicLink))
                    .filter(Files::isDirectory)
                    .map(this::mangaSeries)
                    .toList();
        }
    }

    private List<SeriesEntry> scanAnime() throws IOException {
        try (Stream<Path> paths = Files.list(animeRoot)) {
            return paths.filter(Predicate.not(Files::isSymbolicLink))
                    .filter(Files::isDirectory)
                    .map(this::animeSeries)
                    .toList();
        }
    }

    private SeriesEntry mangaSeries(Path directory) {
        String relativePath = relative(directory);
        String fallbackTitle = directory.getFileName().toString();
        LocalSeriesMetadata metadata = metadata(directory, fallbackTitle);
        Map<Double, UnitMetadata> overrides = units(directory.resolve("chapters.json"), "chapter_number");
        List<ChapterEntry> chapters;
        try (Stream<Path> paths = Files.list(directory)) {
            chapters = paths.filter(Predicate.not(Files::isSymbolicLink))
                    .filter(path -> chapterDirectory(path) || archive(path))
                    .map(path -> chapter(directory, path, overrides))
                    .sorted(UNIT_ORDER)
                    .limit(MAX_UNITS_PER_SERIES + 1L)
                    .toList();
        } catch (IOException exception) {
            throw failure("scan local manga " + relativePath, exception);
        }
        requireUnitLimit(chapters.size(), relativePath);
        LocalPublicationId id = new LocalPublicationId(LocalPublicationType.MANGA_SERIES, relativePath);
        return new SeriesEntry(
                new LocalPublication(id, metadata.title()),
                metadata,
                cover(directory),
                List.copyOf(chapters),
                List.of());
    }

    private SeriesEntry animeSeries(Path directory) {
        String relativePath = relative(directory);
        String fallbackTitle = directory.getFileName().toString();
        LocalSeriesMetadata metadata = metadata(directory, fallbackTitle);
        Map<Double, UnitMetadata> overrides = units(directory.resolve("episodes.json"), "episode_number");
        List<EpisodeEntry> episodes;
        try (Stream<Path> paths = Files.list(directory)) {
            episodes = paths.filter(Predicate.not(Files::isSymbolicLink))
                    .filter(Files::isRegularFile)
                    .filter(AniyomiLocalIndex::video)
                    .map(path -> episode(directory, path, overrides))
                    .sorted(UNIT_ORDER)
                    .limit(MAX_UNITS_PER_SERIES + 1L)
                    .toList();
        } catch (IOException exception) {
            throw failure("scan local anime " + relativePath, exception);
        }
        requireUnitLimit(episodes.size(), relativePath);
        LocalPublicationId id = new LocalPublicationId(LocalPublicationType.ANIME_SERIES, relativePath);
        return new SeriesEntry(
                new LocalPublication(id, metadata.title()),
                metadata,
                cover(directory),
                List.of(),
                List.copyOf(episodes));
    }

    private ChapterEntry chapter(Path series, Path path, Map<Double, UnitMetadata> overrides) {
        String relative = series.relativize(path).toString().replace('\\', '/');
        String fallback = Files.isDirectory(path) ? path.getFileName().toString() : withoutExtension(path);
        double number = number(fallback);
        UnitMetadata override = overrides.get(number);
        return new ChapterEntry(
                relative,
                override == null ? fallback : override.name().orElse(fallback),
                number,
                override == null ? Optional.empty() : override.uploadedAt(),
                override == null ? Optional.empty() : override.scanlator(),
                path,
                Files.isRegularFile(path));
    }

    private EpisodeEntry episode(Path series, Path video, Map<Double, UnitMetadata> overrides) {
        String relative = series.relativize(video).toString().replace('\\', '/');
        String fallback = withoutExtension(video);
        double number = number(fallback);
        UnitMetadata override = overrides.get(number);
        return new EpisodeEntry(
                relative,
                override == null ? fallback : override.name().orElse(fallback),
                number,
                override == null ? Optional.empty() : override.uploadedAt(),
                override == null ? Optional.empty() : override.scanlator(),
                video,
                episodeThumbnail(series, fallback),
                subtitles(series, fallback));
    }

    private LocalSeriesMetadata metadata(Path directory, String fallbackTitle) {
        Path file = directory.resolve("details.json");
        if (!regular(file)) {
            return LocalSeriesMetadata.defaults(fallbackTitle);
        }
        Map<String, Object> values = object(readJson(file), file);
        String title = optionalText(values, "title", file).orElse(fallbackTitle);
        List<String> genres = array(values.get("genre"), "genre", file).stream()
                .map(value -> requiredText(value, "genre", file))
                .toList();
        return new LocalSeriesMetadata(
                title,
                optionalText(values, "author", file),
                optionalText(values, "artist", file),
                optionalText(values, "description", file).orElse(""),
                genres,
                status(values.get("status"), file));
    }

    private Map<Double, UnitMetadata> units(Path file, String numberKey) {
        if (!regular(file)) {
            return Map.of();
        }
        Map<Double, UnitMetadata> result = new LinkedHashMap<>();
        for (Object value : array(readJson(file), "root", file)) {
            Map<String, Object> entry = object(value, file);
            double number = requiredNumber(entry.get(numberKey), numberKey, file);
            UnitMetadata previous = result.put(number, new UnitMetadata(
                    optionalText(entry, "name", file),
                    optionalInstant(entry.get("date_upload"), file),
                    optionalText(entry, "scanlator", file)));
            if (previous != null) {
                throw invalid(file, "contains duplicate " + numberKey + " " + number);
            }
        }
        return Map.copyOf(result);
    }

    private Optional<URI> cover(Path directory) {
        return fileUri(directory.resolve("cover.jpg"));
    }

    private Optional<URI> episodeThumbnail(Path directory, String baseName) {
        for (Path parent : List.of(directory, directory.resolve("thumbnails"))) {
            for (String extension : List.of("jpg", "jpeg", "png", "webp")) {
                Optional<URI> match = fileUri(parent.resolve(baseName + "." + extension));
                if (match.isPresent()) {
                    return match;
                }
            }
        }
        return Optional.empty();
    }

    private List<SourceSubtitleTrack> subtitles(Path directory, String baseName) {
        try (Stream<Path> paths = Files.list(directory)) {
            return paths.filter(Predicate.not(Files::isSymbolicLink))
                    .filter(Files::isRegularFile)
                    .filter(path -> subtitleFor(path, baseName))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString(), NAME_ORDER))
                    .map(path -> subtitle(path, baseName))
                    .toList();
        } catch (IOException exception) {
            throw failure("scan subtitles for " + baseName, exception);
        }
    }

    private SourceSubtitleTrack subtitle(Path path, String baseName) {
        String fileName = path.getFileName().toString();
        String middle = fileName.substring(baseName.length(), fileName.lastIndexOf('.'));
        String language = middle.startsWith(".") ? middle.substring(1) : "";
        return new SourceSubtitleTrack(
                fileName,
                language.isBlank() ? "Subtitles" : language,
                language.isBlank() ? Optional.empty() : Optional.of(language),
                path.toUri(),
                Map.of());
    }

    private String relative(Path path) {
        return root.relativize(path).toString().replace('\\', '/');
    }

    private static Optional<URI> fileUri(Path path) {
        return regular(path) ? Optional.of(path.toUri()) : Optional.empty();
    }

    private static boolean regular(Path path) {
        return !Files.isSymbolicLink(path) && Files.isRegularFile(path);
    }

    private static boolean chapterDirectory(Path path) {
        if (!Files.isDirectory(path)) {
            return false;
        }
        try (Stream<Path> files = Files.walk(path)) {
            return files.filter(Predicate.not(Files::isSymbolicLink))
                    .filter(Files::isRegularFile)
                    .anyMatch(AniyomiLocalIndex::image);
        } catch (IOException exception) {
            throw failure("inspect chapter folder " + path.getFileName(), exception);
        }
    }

    private static boolean image(Path path) {
        return IMAGES.contains(extension(path));
    }

    private static boolean archive(Path path) {
        return Files.isRegularFile(path) && ARCHIVES.contains(extension(path));
    }

    private static boolean video(Path path) {
        return VIDEOS.contains(extension(path));
    }

    private static boolean subtitleFor(Path path, String baseName) {
        String name = path.getFileName().toString();
        return name.startsWith(baseName + ".") && SUBTITLES.contains(extension(path));
    }

    private static String extension(Path path) {
        String name = path.getFileName().toString();
        int separator = name.lastIndexOf('.');
        return separator < 0 ? "" : name.substring(separator + 1).toLowerCase(Locale.ROOT);
    }

    private static String withoutExtension(Path path) {
        String name = path.getFileName().toString();
        int separator = name.lastIndexOf('.');
        return separator <= 0 ? name : name.substring(0, separator);
    }

    private static double number(String value) {
        Matcher matcher = NUMBER.matcher(value);
        if (!matcher.find()) {
            return -1.0D;
        }
        try {
            return Double.parseDouble(matcher.group(1));
        } catch (NumberFormatException exception) {
            return -1.0D;
        }
    }

    private static Object readJson(Path file) {
        try {
            long size = Files.size(file);
            if (size > MAX_JSON_BYTES) {
                throw invalid(file, "exceeds the JSON size limit");
            }
            return LocalJsonParser.parse(Files.readString(file, StandardCharsets.UTF_8));
        } catch (IOException exception) {
            throw failure("read " + file.getFileName(), exception);
        } catch (IllegalArgumentException exception) {
            throw invalid(file, exception.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> object(Object value, Path file) {
        if (!(value instanceof Map<?, ?> map)) {
            throw invalid(file, "must contain a JSON object");
        }
        return (Map<String, Object>) map;
    }

    private static List<Object> array(Object value, String name, Path file) {
        if (value == null) {
            return List.of();
        }
        if (!(value instanceof List<?> list)) {
            throw invalid(file, name + " must contain a JSON array");
        }
        return List.copyOf(list);
    }

    private static Optional<String> optionalText(
            Map<String, Object> values,
            String name,
            Path file) {
        Object value = values.get(name);
        if (value == null) {
            return Optional.empty();
        }
        if (!(value instanceof String text)) {
            throw invalid(file, name + " must be a JSON string");
        }
        return Optional.of(text.strip()).filter(textValue -> !textValue.isEmpty());
    }

    private static String requiredText(Object value, String name, Path file) {
        if (!(value instanceof String text) || text.isBlank()) {
            throw invalid(file, name + " entries must be non-blank strings");
        }
        return text.strip();
    }

    private static double requiredNumber(Object value, String name, Path file) {
        if (!(value instanceof Number number)) {
            throw invalid(file, name + " must be a JSON number");
        }
        double result = number.doubleValue();
        if (!Double.isFinite(result) || result < 0) {
            throw invalid(file, name + " must be finite and non-negative");
        }
        return result;
    }

    private static Optional<Instant> optionalInstant(Object value, Path file) {
        if (value == null) {
            return Optional.empty();
        }
        String text = requiredText(value, "date_upload", file);
        try {
            if (text.endsWith("Z")) {
                return Optional.of(Instant.parse(text));
            }
            if (text.matches(".*[+-][0-9]{2}:[0-9]{2}$")) {
                return Optional.of(OffsetDateTime.parse(text).toInstant());
            }
            return Optional.of(LocalDateTime.parse(text).toInstant(ZoneOffset.UTC));
        } catch (DateTimeException exception) {
            throw invalid(file, "date_upload must use ISO-8601");
        }
    }

    private static LocalSeriesStatus status(Object value, Path file) {
        if (value == null) {
            return LocalSeriesStatus.UNKNOWN;
        }
        String token = value instanceof Number number
                ? Integer.toString(number.intValue())
                : requiredText(value, "status", file);
        try {
            int index = Integer.parseInt(token);
            return LocalSeriesStatus.values()[index];
        } catch (NumberFormatException | IndexOutOfBoundsException exception) {
            throw invalid(file, "status must be between 0 and 6");
        }
    }

    private static void requireUnitLimit(int count, String series) {
        if (count > MAX_UNITS_PER_SERIES) {
            throw new LocalSourceException(
                    "Local series " + series + " exceeds the unit limit of " + MAX_UNITS_PER_SERIES);
        }
    }

    private static LocalSourceException invalid(Path file, String message) {
        return new LocalSourceException("Invalid " + file.getFileName() + ": " + message);
    }

    private static LocalSourceException failure(String operation, IOException cause) {
        return new LocalSourceException("Unable to " + operation, cause);
    }

    record Snapshot(LocalSourceScan report, List<SeriesEntry> series) {
    }

    record SeriesEntry(
            LocalPublication publication,
            LocalSeriesMetadata metadata,
            Optional<URI> cover,
            List<ChapterEntry> chapters,
            List<EpisodeEntry> episodes) {
        boolean manga() {
            return publication.id().type() == LocalPublicationType.MANGA_SERIES;
        }
    }

    private interface UnitEntry {
        String relativePath();

        String title();

        double number();
    }

    record ChapterEntry(
            String relativePath,
            String title,
            double number,
            Optional<Instant> uploadedAt,
            Optional<String> scanlator,
            Path path,
            boolean archive) implements UnitEntry {
    }

    record EpisodeEntry(
            String relativePath,
            String title,
            double number,
            Optional<Instant> uploadedAt,
            Optional<String> scanlator,
            Path video,
            Optional<URI> thumbnail,
            List<SourceSubtitleTrack> subtitles) implements UnitEntry {
    }

    private record UnitMetadata(
            Optional<String> name,
            Optional<Instant> uploadedAt,
            Optional<String> scanlator) {
    }
}
