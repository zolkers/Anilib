package fr.vriege.anilib.feature.downloads.runtime;

import fr.vriege.anilib.feature.downloads.DownloadId;
import fr.vriege.anilib.feature.downloads.DownloadPriority;
import fr.vriege.anilib.feature.downloads.DownloadStatus;
import fr.vriege.anilib.feature.library.LibraryItemId;
import fr.vriege.anilib.feature.source.SourceCatalogueItemId;
import fr.vriege.anilib.feature.source.SourceContentUnit;
import fr.vriege.anilib.feature.source.SourceContentUnitId;
import fr.vriege.anilib.feature.source.SourceId;
import fr.vriege.anilib.feature.source.SourcePageResource;
import fr.vriege.anilib.feature.source.SourceStreamFormat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.Map;

final class FileDownloadQueueStore {
    private static final String HEADER_V1 = "ANILIB_DOWNLOADS\t1\t";
    private static final String HEADER_V2 = "ANILIB_DOWNLOADS\t2\t";
    private static final String HEADER_V3 = "ANILIB_DOWNLOADS\t3\t";
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();
    private final Path file;

    FileDownloadQueueStore(Path file) {
        this.file = file.toAbsolutePath().normalize();
    }

    LoadResult load() throws IOException {
        if (!Files.exists(file)) {
            return new LoadResult(false, List.of());
        }
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        if (lines.isEmpty()
                || !lines.getFirst().startsWith(HEADER_V1)
                && !lines.getFirst().startsWith(HEADER_V2)
                && !lines.getFirst().startsWith(HEADER_V3)) {
            throw new IOException("Unsupported downloads queue format");
        }
        int version = lines.getFirst().startsWith(HEADER_V3)
                ? 3
                : lines.getFirst().startsWith(HEADER_V2) ? 2 : 1;
        String header = version == 3 ? HEADER_V3 : version == 2 ? HEADER_V2 : HEADER_V1;
        boolean offline = Boolean.parseBoolean(lines.getFirst().substring(header.length()));
        List<DownloadRecord> records = new ArrayList<>();
        for (int index = 1; index < lines.size(); index++) {
            if (!lines.get(index).isBlank()) {
                records.add(decode(lines.get(index), index - 1L, version));
            }
        }
        return new LoadResult(offline, List.copyOf(records));
    }

    void save(boolean offlineMode, Collection<DownloadRecord> records) throws IOException {
        Path parent = file.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        List<String> lines = new ArrayList<>();
        lines.add(HEADER_V3 + offlineMode);
        records.stream()
                .sorted(Comparator.comparingLong((DownloadRecord record) -> record.queueOrder)
                        .thenComparing(record -> record.id))
                .map(this::encode)
                .forEach(lines::add);
        Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
        Files.write(temporary, lines, StandardCharsets.UTF_8);
        try {
            Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private String encode(DownloadRecord record) {
        String pageValues = record.pages.stream().map(page -> text(page.value())).collect(
                Collectors.joining(","));
        String pageSizes = record.pages.stream().map(page -> Long.toString(page.estimatedBytes())).collect(
                Collectors.joining(","));
        VideoDownloadMetadata video = record.video;
        return String.join("\t",
                "JOB",
                record.id.toString(),
                text(record.libraryItemId.value()),
                text(record.title),
                record.sourceItemId.sourceId().toString(),
                text(record.sourceItemId.value()),
                text(record.contentUnit.id().value()),
                text(record.contentUnit.title()),
                record.contentUnit.publishedAt().map(Instant::toString).orElse(""),
                record.status.name(),
                Integer.toString(record.completedPages),
                Long.toString(record.downloadedBytes),
                text(record.error == null ? "" : record.error),
                record.updatedAt.toString(),
                pageValues,
                pageSizes,
                record.priority.name(),
                Long.toString(record.queueOrder),
                video == null ? "PAGED" : video.format().name(),
                text(video == null ? "" : encodeHeaders(video.headers())),
                text(video == null ? "" : video.offlinePlaylist()),
                Double.toString(record.contentUnit.number()));
    }

    private DownloadRecord decode(String line, long fallbackOrder, int version) throws IOException {
        try {
            String[] fields = line.split("\t", -1);
            int expectedFields = version == 3 ? 22 : version == 2 ? 18 : 16;
            if (fields.length != expectedFields || !fields[0].equals("JOB")) {
                throw new IllegalArgumentException("invalid job field count");
            }
            SourceCatalogueItemId itemId = new SourceCatalogueItemId(
                    SourceId.of(fields[4]),
                    plain(fields[5]));
            SourceContentUnitId unitId = new SourceContentUnitId(itemId, plain(fields[6]));
            SourceContentUnit unit = new SourceContentUnit(
                    unitId,
                    plain(fields[7]),
                    version == 3 ? Double.parseDouble(fields[21]) : SourceContentUnit.UNKNOWN_NUMBER,
                    fields[8].isEmpty() ? Optional.empty() : Optional.of(Instant.parse(fields[8])));
            String[] pageValues = fields[14].split(",", -1);
            String[] pageSizes = fields[15].split(",", -1);
            if (pageValues.length != pageSizes.length || pageValues.length == 0) {
                throw new IllegalArgumentException("invalid page metadata");
            }
            List<SourcePageResource> pages = new ArrayList<>();
            for (int index = 0; index < pageValues.length; index++) {
                pages.add(new SourcePageResource(
                        unitId,
                        plain(pageValues[index]),
                        index,
                        Long.parseLong(pageSizes[index])));
            }
            String error = plain(fields[12]);
            VideoDownloadMetadata video = version == 3 && !fields[18].equals("PAGED")
                    ? new VideoDownloadMetadata(
                            SourceStreamFormat.valueOf(fields[18]),
                            decodeHeaders(plain(fields[19])),
                            plain(fields[20]))
                    : null;
            return new DownloadRecord(
                    DownloadId.parse(fields[1]),
                    new LibraryItemId(plain(fields[2])),
                    plain(fields[3]),
                    itemId,
                    unit,
                    pages,
                    video,
                    version >= 2 ? DownloadPriority.valueOf(fields[16]) : DownloadPriority.NORMAL,
                    version >= 2 ? Long.parseLong(fields[17]) : fallbackOrder,
                    DownloadStatus.valueOf(fields[9]),
                    Integer.parseInt(fields[10]),
                    Long.parseLong(fields[11]),
                    error.isEmpty() ? null : error,
                    Instant.parse(fields[13]));
        } catch (IllegalArgumentException exception) {
            throw new IOException("Invalid downloads queue entry", exception);
        }
    }

    private static String encodeHeaders(Map<String, String> headers) {
        return headers.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> text(entry.getKey()) + ':' + text(entry.getValue()))
                .collect(Collectors.joining(","));
    }

    private static Map<String, String> decodeHeaders(String value) {
        if (value.isEmpty()) {
            return Map.of();
        }
        return Arrays.stream(value.split(","))
                .map(entry -> entry.split(":", 2))
                .collect(Collectors.toUnmodifiableMap(entry -> plain(entry[0]), entry -> plain(entry[1])));
    }

    private static String text(String value) {
        return ENCODER.encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String plain(String value) {
        return new String(DECODER.decode(value), StandardCharsets.UTF_8);
    }

    record LoadResult(boolean offlineMode, List<DownloadRecord> records) {
    }
}
