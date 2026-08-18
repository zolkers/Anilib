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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

final class FileDownloadQueueStore {
    private static final String HEADER_V1 = "ANILIB_DOWNLOADS\t1\t";
    private static final String HEADER_V2 = "ANILIB_DOWNLOADS\t2\t";
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
                && !lines.getFirst().startsWith(HEADER_V2)) {
            throw new IOException("Unsupported downloads queue format");
        }
        boolean versionTwo = lines.getFirst().startsWith(HEADER_V2);
        String header = versionTwo ? HEADER_V2 : HEADER_V1;
        boolean offline = Boolean.parseBoolean(lines.getFirst().substring(header.length()));
        List<DownloadRecord> records = new ArrayList<>();
        for (int index = 1; index < lines.size(); index++) {
            if (!lines.get(index).isBlank()) {
                records.add(decode(lines.get(index), index - 1L, versionTwo));
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
        lines.add(HEADER_V2 + offlineMode);
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
                java.util.stream.Collectors.joining(","));
        String pageSizes = record.pages.stream().map(page -> Long.toString(page.estimatedBytes())).collect(
                java.util.stream.Collectors.joining(","));
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
                Long.toString(record.queueOrder));
    }

    private DownloadRecord decode(String line, long fallbackOrder, boolean versionTwo) throws IOException {
        try {
            String[] fields = line.split("\t", -1);
            int expectedFields = versionTwo ? 18 : 16;
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
            return new DownloadRecord(
                    DownloadId.parse(fields[1]),
                    new LibraryItemId(plain(fields[2])),
                    plain(fields[3]),
                    itemId,
                    unit,
                    pages,
                    versionTwo ? DownloadPriority.valueOf(fields[16]) : DownloadPriority.NORMAL,
                    versionTwo ? Long.parseLong(fields[17]) : fallbackOrder,
                    DownloadStatus.valueOf(fields[9]),
                    Integer.parseInt(fields[10]),
                    Long.parseLong(fields[11]),
                    error.isEmpty() ? null : error,
                    Instant.parse(fields[13]));
        } catch (IllegalArgumentException exception) {
            throw new IOException("Invalid downloads queue entry", exception);
        }
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
