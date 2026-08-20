package fr.vriege.anilib.feature.backup.runtime;

import fr.vriege.anilib.feature.backup.AniyomiBackupImportResult;
import fr.vriege.anilib.feature.backup.AniyomiBackupInspection;
import fr.vriege.anilib.feature.backup.BackupException;
import fr.vriege.anilib.feature.library.LibraryCatalog;
import fr.vriege.anilib.feature.library.LibraryHistoryEntry;
import fr.vriege.anilib.feature.library.LibraryItem;
import fr.vriege.anilib.feature.library.LibraryItemId;
import fr.vriege.anilib.feature.library.LibraryOrigin;
import fr.vriege.anilib.feature.library.LibraryProgress;
import fr.vriege.anilib.feature.library.LibraryTitleMetadata;
import fr.vriege.anilib.feature.library.MediaKind;
import fr.vriege.anilib.feature.library.PublicationStatus;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.zip.GZIPInputStream;
import java.util.stream.Collectors;

final class AniyomiBackupImporter {
    private static final long MAX_FILE_BYTES = 256L * 1024L * 1024L;
    private static final int MAX_DECOMPRESSED_BYTES = 512 * 1024 * 1024;
    private static final int MAX_TITLES = 1_000_000;
    private static final int MAX_CONTENT_PER_TITLE = 100_000;
    private static final int MAX_TEXT_BYTES = 1_048_576;
    private static final Set<Integer> UNSUPPORTED_ROOT_FIELDS = Set.of(
            101, 103, 104, 105, 106, 107, 108, 109, 503, 504, 505, 506);

    private final LibraryCatalog catalog;
    private final Clock clock;

    AniyomiBackupImporter(LibraryCatalog catalog, Clock clock) {
        this.catalog = Objects.requireNonNull(catalog, "catalog must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    AniyomiBackupInspection inspect(Path path) {
        return read(path).inspection();
    }

    AniyomiBackupImportResult importBackup(Path path) {
        ParsedBackup parsed = read(path);
        List<LibraryItem> before = catalog.snapshot();
        Map<LibraryItemId, LibraryItem> merged = new LinkedHashMap<>();
        Map<LibraryOrigin, LibraryItemId> origins = new LinkedHashMap<>();
        before.forEach(item -> {
            merged.put(item.id(), item);
            item.origin().ifPresent(origin -> origins.put(origin, item.id()));
        });
        int created = 0;
        int updated = 0;
        for (LibraryItem imported : parsed.items()) {
            LibraryOrigin origin = imported.origin().orElseThrow();
            LibraryItemId existingId = origins.get(origin);
            if (existingId == null) {
                LibraryItem collision = merged.get(imported.id());
                if (collision != null && !collision.origin().equals(imported.origin())) {
                    throw new BackupException("Aniyomi import identity conflicts with an existing title");
                }
                merged.put(imported.id(), imported);
                origins.put(origin, imported.id());
                created++;
            } else {
                LibraryItem existing = merged.get(existingId);
                merged.put(existingId, merge(existing, imported, existingId));
                updated++;
            }
        }
        catalog.replaceAll(merged.values());
        return new AniyomiBackupImportResult(clock.instant(), created, updated, parsed.inspection());
    }

    private static LibraryItem merge(
            LibraryItem existing,
            LibraryItem imported,
            LibraryItemId existingId) {
        Set<String> categories = new LinkedHashSet<>(existing.categories());
        categories.addAll(imported.categories());
        List<LibraryHistoryEntry> history = mergeHistory(existing.history(), imported.history());
        Optional<LibraryProgress> progress = newestProgress(existing.progress(), imported.progress());
        return new LibraryItem(
                existingId,
                imported.title(),
                imported.kind(),
                existing.addedAt().isBefore(imported.addedAt()) ? existing.addedAt() : imported.addedAt(),
                categories,
                existing.favorite() || imported.favorite(),
                progress,
                history,
                imported.metadata(),
                imported.origin());
    }

    private static List<LibraryHistoryEntry> mergeHistory(
            List<LibraryHistoryEntry> existing,
            List<LibraryHistoryEntry> imported) {
        Map<String, LibraryHistoryEntry> entries = new LinkedHashMap<>();
        for (LibraryHistoryEntry entry : existing) {
            entries.put(historyKey(entry), entry);
        }
        for (LibraryHistoryEntry entry : imported) {
            entries.put(historyKey(entry), entry);
        }
        return entries.values().stream()
                .sorted(Comparator.comparing(LibraryHistoryEntry::openedAt)
                        .thenComparing(LibraryHistoryEntry::contentId))
                .toList();
    }

    private static String historyKey(LibraryHistoryEntry entry) {
        return entry.contentId() + '\u0000' + entry.openedAt();
    }

    private static Optional<LibraryProgress> newestProgress(
            Optional<LibraryProgress> existing,
            Optional<LibraryProgress> imported) {
        if (existing.isEmpty()) {
            return imported;
        }
        if (imported.isEmpty()) {
            return existing;
        }
        return imported.orElseThrow().updatedAt().isAfter(existing.orElseThrow().updatedAt())
                ? imported
                : existing;
    }

    private static ParsedBackup read(Path path) {
        Path normalized = Objects.requireNonNull(path, "path must not be null").toAbsolutePath().normalize();
        try {
            if (!Files.isRegularFile(normalized) || Files.isSymbolicLink(normalized)) {
                throw new BackupException("Aniyomi backup must be a regular non-symbolic file");
            }
            long size = Files.size(normalized);
            if (size > MAX_FILE_BYTES) {
                throw new BackupException("Aniyomi backup exceeds the 256 MB input limit");
            }
            byte[] payload = readPayload(normalized);
            MutableBackup decoded = decodeRoot(new ProtoReader(payload));
            if (decoded.titles.isEmpty()) {
                throw new BackupException("Aniyomi backup contains no manga or anime titles");
            }
            List<LibraryItem> items = decoded.toLibraryItems();
            AniyomiBackupInspection inspection = new AniyomiBackupInspection(
                    normalized,
                    size,
                    decoded.mangaCount,
                    decoded.animeCount,
                    decoded.categoryCount(),
                    items.stream().mapToInt(item -> item.history().size()).sum(),
                    (int) items.stream().filter(item -> item.progress().isPresent()).count(),
                    decoded.skippedEntries);
            return new ParsedBackup(inspection, items);
        } catch (BackupException exception) {
            throw exception;
        } catch (IOException | IllegalArgumentException exception) {
            throw new BackupException("Invalid or unsupported Aniyomi backup", exception);
        }
    }

    private static byte[] readPayload(Path path) throws IOException {
        try (BufferedInputStream input = new BufferedInputStream(Files.newInputStream(path))) {
            input.mark(2);
            int first = input.read();
            int second = input.read();
            input.reset();
            InputStream decoded = first == 0x1f && second == 0x8b
                    ? new GZIPInputStream(input)
                    : input;
            return readBounded(decoded);
        }
    }

    private static byte[] readBounded(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[16_384];
        int total = 0;
        int read;
        while ((read = input.read(buffer)) != -1) {
            total = Math.addExact(total, read);
            if (total > MAX_DECOMPRESSED_BYTES) {
                throw new IOException("Aniyomi backup exceeds the 512 MB decoded limit");
            }
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    private static MutableBackup decodeRoot(ProtoReader input) throws IOException {
        MutableBackup backup = new MutableBackup();
        while (input.hasRemaining()) {
            Field field = input.nextField();
            if (field.number() == 1) {
                backup.addTitle(parseTitle(input.readMessage(field), MediaKind.MANGA));
            } else if (field.number() == 2) {
                backup.mangaCategories.add(parseCategory(input.readMessage(field)));
            } else if (field.number() == 3 || field.number() == 501) {
                backup.addTitle(parseTitle(input.readMessage(field), MediaKind.ANIME));
            } else if (field.number() == 4 || field.number() == 502) {
                backup.animeCategories.add(parseCategory(input.readMessage(field)));
            } else {
                if (UNSUPPORTED_ROOT_FIELDS.contains(field.number())) {
                    backup.skippedEntries++;
                }
                input.skip(field);
            }
        }
        return backup;
    }

    private static ParsedCategory parseCategory(ProtoReader input) throws IOException {
        String name = "";
        long order = 0;
        while (input.hasRemaining()) {
            Field field = input.nextField();
            if (field.number() == 1) {
                name = input.readString(field);
            } else if (field.number() == 2) {
                order = input.readVarint(field);
            } else {
                input.skip(field);
            }
        }
        if (name.isBlank()) {
            throw new IOException("Aniyomi category name is blank");
        }
        return new ParsedCategory(order, name);
    }

    private static ParsedTitle parseTitle(ProtoReader input, MediaKind kind) throws IOException {
        long source = 0;
        String url = "";
        String title = "";
        String artist = "";
        String author = "";
        String description = "";
        int status = 0;
        long addedAt = 0;
        boolean favorite = true;
        List<Long> categoryOrders = new ArrayList<>();
        List<ParsedContent> content = new ArrayList<>();
        List<ParsedHistory> history = new ArrayList<>();
        int skipped = 0;
        while (input.hasRemaining()) {
            Field field = input.nextField();
            switch (field.number()) {
                case 1 -> source = input.readVarint(field);
                case 2 -> url = input.readString(field);
                case 3 -> title = input.readString(field);
                case 4 -> artist = input.readString(field);
                case 5 -> author = input.readString(field);
                case 6 -> description = input.readString(field);
                case 8 -> status = Math.toIntExact(input.readVarint(field));
                case 13 -> addedAt = input.readVarint(field);
                case 16 -> {
                    if (content.size() >= MAX_CONTENT_PER_TITLE) {
                        throw new IOException("Too many chapters or episodes in Aniyomi title");
                    }
                    content.add(parseContent(input.readMessage(field), kind));
                }
                case 17 -> readRepeatedLong(input, field, categoryOrders);
                case 18 -> {
                    skipped++;
                    input.skip(field);
                }
                case 100 -> favorite = input.readVarint(field) != 0;
                case 104 -> {
                    if (history.size() >= MAX_CONTENT_PER_TITLE) {
                        throw new IOException("Too many history entries in Aniyomi title");
                    }
                    history.add(parseHistory(input.readMessage(field)));
                }
                default -> input.skip(field);
            }
        }
        if (url.isBlank() || title.isBlank()) {
            throw new IOException("Aniyomi title is missing its URL or title");
        }
        return new ParsedTitle(
                source,
                url,
                title,
                artist,
                author,
                description,
                status,
                addedAt,
                favorite,
                List.copyOf(categoryOrders),
                List.copyOf(content),
                List.copyOf(history),
                skipped,
                kind);
    }

    private static void readRepeatedLong(
            ProtoReader input,
            Field field,
            List<Long> destination) throws IOException {
        if (field.wireType() == 0) {
            destination.add(input.readVarint(field));
            return;
        }
        ProtoReader packed = input.readMessage(field);
        while (packed.hasRemaining()) {
            destination.add(packed.readRawVarint());
        }
    }

    private static ParsedContent parseContent(ProtoReader input, MediaKind kind) throws IOException {
        String url = "";
        boolean complete = false;
        long position = 0;
        long extent = kind == MediaKind.ANIME ? 0 : LibraryProgress.UNKNOWN_EXTENT;
        long fetchedAt = 0;
        long uploadedAt = 0;
        long modifiedAt = 0;
        float number = 0;
        while (input.hasRemaining()) {
            Field field = input.nextField();
            switch (field.number()) {
                case 1 -> url = input.readString(field);
                case 4 -> complete = input.readVarint(field) != 0;
                case 6 -> position = Math.max(0, input.readVarint(field));
                case 7 -> fetchedAt = input.readVarint(field);
                case 8 -> uploadedAt = input.readVarint(field);
                case 9 -> number = input.readFloat(field);
                case 11 -> modifiedAt = input.readVarint(field);
                case 16 -> extent = Math.max(0, input.readVarint(field));
                default -> input.skip(field);
            }
        }
        if (url.isBlank()) {
            throw new IOException("Aniyomi chapter or episode URL is blank");
        }
        if (complete) {
            if (extent > 0) {
                position = extent;
            } else {
                position = 1;
                extent = 1;
            }
        } else if (extent == 0) {
            extent = LibraryProgress.UNKNOWN_EXTENT;
        } else if (position > extent) {
            extent = position;
        }
        long timestamp = Math.max(modifiedAt, Math.max(fetchedAt, uploadedAt));
        return new ParsedContent(url, position, extent, number, timestamp);
    }

    private static ParsedHistory parseHistory(ProtoReader input) throws IOException {
        String url = "";
        long lastRead = 0;
        while (input.hasRemaining()) {
            Field field = input.nextField();
            if (field.number() == 1) {
                url = input.readString(field);
            } else if (field.number() == 2) {
                lastRead = input.readVarint(field);
            } else {
                input.skip(field);
            }
        }
        if (url.isBlank() || lastRead <= 0) {
            throw new IOException("Aniyomi history entry is missing its URL or timestamp");
        }
        return new ParsedHistory(url, lastRead);
    }

    private static Instant instant(long epochMillis, Instant fallback) {
        if (epochMillis <= 0) {
            return fallback;
        }
        return Instant.ofEpochMilli(epochMillis);
    }

    private static PublicationStatus publicationStatus(int value) {
        return switch (value) {
            case 1 -> PublicationStatus.ONGOING;
            case 2, 4 -> PublicationStatus.COMPLETED;
            case 5 -> PublicationStatus.CANCELLED;
            case 6 -> PublicationStatus.HIATUS;
            default -> PublicationStatus.UNKNOWN;
        };
    }

    private static List<String> person(String value) {
        return value.isBlank() ? List.of() : List.of(value.strip());
    }

    private static final class MutableBackup {
        private final List<ParsedTitle> titles = new ArrayList<>();
        private final List<ParsedCategory> mangaCategories = new ArrayList<>();
        private final List<ParsedCategory> animeCategories = new ArrayList<>();
        private int mangaCount;
        private int animeCount;
        private int skippedEntries;

        private void addTitle(ParsedTitle title) throws IOException {
            if (titles.size() >= MAX_TITLES) {
                throw new IOException("Too many titles in Aniyomi backup");
            }
            titles.add(title);
            skippedEntries = Math.addExact(skippedEntries, title.skippedEntries());
            if (title.kind() == MediaKind.MANGA) {
                mangaCount++;
            } else {
                animeCount++;
            }
        }

        private int categoryCount() {
            return Math.addExact(mangaCategories.size(), animeCategories.size());
        }

        private List<LibraryItem> toLibraryItems() throws IOException {
            Map<Long, String> mangaNames = categoryNames(mangaCategories);
            Map<Long, String> animeNames = categoryNames(animeCategories);
            Set<LibraryItemId> identifiers = new HashSet<>();
            List<LibraryItem> items = new ArrayList<>(titles.size());
            for (ParsedTitle title : titles) {
                LibraryItem item = title.toLibraryItem(
                        title.kind() == MediaKind.MANGA ? mangaNames : animeNames);
                if (!identifiers.add(item.id())) {
                    throw new IOException("Duplicate Aniyomi title identity");
                }
                items.add(item);
            }
            return List.copyOf(items);
        }

        private static Map<Long, String> categoryNames(List<ParsedCategory> categories) throws IOException {
            Map<Long, String> names = new LinkedHashMap<>();
            for (ParsedCategory category : categories) {
                if (names.putIfAbsent(category.order(), category.name()) != null) {
                    throw new IOException("Duplicate Aniyomi category order");
                }
            }
            return Map.copyOf(names);
        }
    }

    private record ParsedTitle(
            long source,
            String url,
            String title,
            String artist,
            String author,
            String description,
            int status,
            long addedAt,
            boolean favorite,
            List<Long> categoryOrders,
            List<ParsedContent> content,
            List<ParsedHistory> history,
            int skippedEntries,
            MediaKind kind) {

        private LibraryItem toLibraryItem(Map<Long, String> categoryNames) {
            String sourceId = "aniyomi." + Long.toUnsignedString(source);
            LibraryOrigin origin = new LibraryOrigin(sourceId, url);
            String identity = kind.name() + '\u0000' + sourceId + '\u0000' + url;
            LibraryItemId id = new LibraryItemId(UUID.nameUUIDFromBytes(
                    identity.getBytes(StandardCharsets.UTF_8)).toString());
            Set<String> categories = new LinkedHashSet<>();
            categoryOrders.stream().map(categoryNames::get).filter(Objects::nonNull).forEach(categories::add);
            Map<String, ParsedContent> contentByUrl = content.stream().collect(
                    Collectors.toMap(
                            ParsedContent::url,
                            Function.identity(),
                            (first, second) -> second,
                            LinkedHashMap::new));
            List<LibraryHistoryEntry> importedHistory = history.stream()
                    .map(entry -> new LibraryHistoryEntry(
                            entry.url(),
                            Instant.ofEpochMilli(entry.lastRead()),
                            contentByUrl.getOrDefault(entry.url(), ParsedContent.empty(entry.url())).position()))
                    .sorted(Comparator.comparing(LibraryHistoryEntry::openedAt))
                    .toList();
            Optional<LibraryProgress> progress = progress(contentByUrl);
            Instant fallback = Instant.EPOCH;
            Instant importedAt = instant(addedAt, fallback);
            return new LibraryItem(
                    id,
                    title,
                    kind,
                    importedAt,
                    categories,
                    favorite,
                    progress,
                    importedHistory,
                    new LibraryTitleMetadata(
                            description,
                            person(author),
                            person(artist),
                            publicationStatus(status)),
                    Optional.of(origin));
        }

        private Optional<LibraryProgress> progress(Map<String, ParsedContent> contentByUrl) {
            Optional<ParsedHistory> latestHistory = history.stream()
                    .max(Comparator.comparingLong(ParsedHistory::lastRead));
            if (latestHistory.isPresent()) {
                ParsedHistory historyEntry = latestHistory.orElseThrow();
                ParsedContent matched = contentByUrl.get(historyEntry.url());
                if (matched != null && matched.position() > 0) {
                    return Optional.of(new LibraryProgress(
                            matched.url(),
                            matched.position(),
                            matched.extent(),
                            Instant.ofEpochMilli(historyEntry.lastRead())));
                }
            }
            return content.stream()
                    .filter(entry -> entry.position() > 0)
                    .max(Comparator.comparingDouble(ParsedContent::number)
                            .thenComparingLong(ParsedContent::timestamp))
                    .map(entry -> new LibraryProgress(
                            entry.url(),
                            entry.position(),
                            entry.extent(),
                            instant(entry.timestamp(), instant(addedAt, Instant.EPOCH))));
        }
    }

    private record ParsedCategory(long order, String name) {
    }

    private record ParsedContent(
            String url,
            long position,
            long extent,
            float number,
            long timestamp) {
        private static ParsedContent empty(String url) {
            return new ParsedContent(url, 0, LibraryProgress.UNKNOWN_EXTENT, 0, 0);
        }
    }

    private record ParsedHistory(String url, long lastRead) {
    }

    private record ParsedBackup(AniyomiBackupInspection inspection, List<LibraryItem> items) {
        private ParsedBackup {
            items = List.copyOf(items);
        }
    }

    private record Field(int number, int wireType) {
    }

    private static final class ProtoReader {
        private final byte[] data;
        private final int limit;
        private int position;

        private ProtoReader(byte[] data) {
            this(data, 0, data.length);
        }

        private ProtoReader(byte[] data, int position, int limit) {
            this.data = data;
            this.position = position;
            this.limit = limit;
        }

        private boolean hasRemaining() {
            return position < limit;
        }

        private Field nextField() throws IOException {
            long key = readRawVarint();
            int number = Math.toIntExact(key >>> 3);
            int wireType = (int) (key & 7);
            if (number <= 0 || wireType > 5 || wireType == 3 || wireType == 4) {
                throw new IOException("Invalid protobuf field key");
            }
            return new Field(number, wireType);
        }

        private long readVarint(Field field) throws IOException {
            requireWire(field, 0);
            return readRawVarint();
        }

        private long readRawVarint() throws IOException {
            long value = 0;
            for (int shift = 0; shift < 64; shift += 7) {
                if (position >= limit) {
                    throw new IOException("Truncated protobuf varint");
                }
                int current = data[position++] & 0xff;
                value |= (long) (current & 0x7f) << shift;
                if ((current & 0x80) == 0) {
                    return value;
                }
            }
            throw new IOException("Oversized protobuf varint");
        }

        private float readFloat(Field field) throws IOException {
            requireWire(field, 5);
            requireRemaining(4);
            float value = ByteBuffer.wrap(data, position, 4)
                    .order(ByteOrder.LITTLE_ENDIAN)
                    .getFloat();
            position += 4;
            if (!Float.isFinite(value)) {
                throw new IOException("Non-finite protobuf float");
            }
            return value;
        }

        private String readString(Field field) throws IOException {
            ProtoReader bytes = readMessage(field);
            int length = bytes.limit - bytes.position;
            if (length > MAX_TEXT_BYTES) {
                throw new IOException("Aniyomi text field exceeds 1 MB");
            }
            try {
                String value = StandardCharsets.UTF_8.newDecoder()
                        .onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT)
                        .decode(ByteBuffer.wrap(data, bytes.position, length))
                        .toString();
                bytes.position = bytes.limit;
                return value;
            } catch (CharacterCodingException exception) {
                throw new IOException("Invalid UTF-8 in Aniyomi backup", exception);
            }
        }

        private ProtoReader readMessage(Field field) throws IOException {
            requireWire(field, 2);
            long encodedLength = readRawVarint();
            if (encodedLength < 0 || encodedLength > Integer.MAX_VALUE) {
                throw new IOException("Invalid protobuf message length");
            }
            int length = (int) encodedLength;
            requireRemaining(length);
            ProtoReader nested = new ProtoReader(data, position, position + length);
            position += length;
            return nested;
        }

        private void skip(Field field) throws IOException {
            switch (field.wireType()) {
                case 0 -> readRawVarint();
                case 1 -> skipBytes(8);
                case 2 -> {
                    long encodedLength = readRawVarint();
                    if (encodedLength < 0 || encodedLength > Integer.MAX_VALUE) {
                        throw new IOException("Invalid protobuf field length");
                    }
                    skipBytes((int) encodedLength);
                }
                case 5 -> skipBytes(4);
                default -> throw new IOException("Unsupported protobuf wire type");
            }
        }

        private void skipBytes(int count) throws IOException {
            requireRemaining(count);
            position += count;
        }

        private void requireRemaining(int count) throws IOException {
            if (count < 0 || count > limit - position) {
                throw new IOException("Truncated protobuf field");
            }
        }

        private static void requireWire(Field field, int expected) throws IOException {
            if (field.wireType() != expected) {
                throw new IOException("Unexpected protobuf wire type for field " + field.number());
            }
        }
    }
}
