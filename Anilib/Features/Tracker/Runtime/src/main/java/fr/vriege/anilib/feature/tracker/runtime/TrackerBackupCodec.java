package fr.vriege.anilib.feature.tracker.runtime;

import fr.vriege.anilib.feature.library.LibraryItemId;
import fr.vriege.anilib.feature.tracker.TrackerEntry;
import fr.vriege.anilib.feature.tracker.TrackerId;
import fr.vriege.anilib.feature.tracker.TrackerStatus;
import fr.vriege.anilib.framework.backup.BackupCodecException;
import fr.vriege.anilib.framework.backup.BackupSectionCodec;
import fr.vriege.anilib.framework.backup.BackupSectionData;
import fr.vriege.anilib.framework.backup.BackupSectionDetails;
import fr.vriege.anilib.framework.backup.BackupSectionId;
import fr.vriege.anilib.framework.backup.PreparedBackupRestore;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;

/** Tracker-owned versioned backup codec for remote title bindings and local mirrors. */
public final class TrackerBackupCodec implements BackupSectionCodec {
    private static final BackupSectionId SECTION_ID = BackupSectionId.of("tracking");
    private static final int MAGIC = 0x5452414B;
    private static final int CURRENT_VERSION = 1;
    private static final int MAXIMUM_ENTRIES = 1_000_000;
    private static final Comparator<TrackerEntry> ORDER = Comparator
            .comparing((TrackerEntry entry) -> entry.libraryItemId().value())
            .thenComparing(TrackerEntry::trackerId);
    private final DefaultTrackerService service;

    public TrackerBackupCodec(DefaultTrackerService service) {
        this.service = Objects.requireNonNull(service, "service must not be null");
    }

    @Override
    public BackupSectionId sectionId() {
        return SECTION_ID;
    }

    @Override
    public String displayName() {
        return "Tracking";
    }

    @Override
    public int currentVersion() {
        return CURRENT_VERSION;
    }

    @Override
    public BackupSectionData exportSection() {
        List<TrackerEntry> snapshot = service.snapshot();
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream output = new DataOutputStream(bytes)) {
                output.writeInt(MAGIC);
                output.writeInt(CURRENT_VERSION);
                output.writeInt(snapshot.size());
                for (TrackerEntry entry : snapshot.stream().sorted(ORDER).toList()) {
                    write(output, entry);
                }
            }
            return new BackupSectionData(bytes.toByteArray(), snapshot.size());
        } catch (IOException exception) {
            throw new BackupCodecException("Unable to encode tracking entries", exception);
        }
    }

    @Override
    public BackupSectionDetails inspect(int version, byte[] payload) {
        List<TrackerEntry> decoded = decode(version, payload);
        return new BackupSectionDetails(SECTION_ID, displayName(), version, decoded.size());
    }

    @Override
    public PreparedBackupRestore prepareRestore(int version, byte[] payload) {
        List<TrackerEntry> before = service.snapshot();
        Map<Key, TrackerEntry> merged = new LinkedHashMap<>();
        before.forEach(entry -> merged.put(Key.of(entry), entry));
        decode(version, payload).forEach(entry -> merged.merge(
                Key.of(entry), entry, TrackerBackupCodec::newest));
        return new Restore(service, before, List.copyOf(merged.values()));
    }

    private static TrackerEntry newest(TrackerEntry current, TrackerEntry imported) {
        return imported.updatedAt().isAfter(current.updatedAt()) ? imported : current;
    }

    private static void write(DataOutputStream output, TrackerEntry entry) throws IOException {
        output.writeUTF(entry.libraryItemId().value());
        output.writeUTF(entry.trackerId().value());
        output.writeUTF(entry.remoteId());
        output.writeUTF(entry.title());
        output.writeDouble(entry.progress());
        output.writeLong(entry.totalUnits());
        output.writeUTF(entry.status().name());
        output.writeBoolean(entry.score().isPresent());
        if (entry.score().isPresent()) {
            output.writeDouble(entry.score().getAsDouble());
        }
        writeOptional(output, entry.startDate().map(LocalDate::toString));
        writeOptional(output, entry.finishDate().map(LocalDate::toString));
        output.writeBoolean(entry.privateEntry());
        writeOptional(output, entry.remoteUri().map(URI::toString));
        output.writeUTF(entry.updatedAt().toString());
    }

    private static List<TrackerEntry> decode(int version, byte[] payload) {
        if (version != CURRENT_VERSION) {
            throw new BackupCodecException("Unsupported tracking backup version: " + version);
        }
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(
                Objects.requireNonNull(payload, "payload must not be null")))) {
            if (input.readInt() != MAGIC || input.readInt() != version) {
                throw new BackupCodecException("Tracking section signature or version is invalid");
            }
            int count = input.readInt();
            if (count < 0 || count > MAXIMUM_ENTRIES) {
                throw new BackupCodecException("Invalid tracking entry count: " + count);
            }
            List<TrackerEntry> decoded = new ArrayList<>();
            Map<Key, TrackerEntry> unique = new LinkedHashMap<>();
            for (int index = 0; index < count; index++) {
                boolean hasScore;
                LibraryItemId itemId = new LibraryItemId(input.readUTF());
                TrackerId trackerId = TrackerId.of(input.readUTF());
                String remoteId = input.readUTF();
                String title = input.readUTF();
                double progress = input.readDouble();
                long total = input.readLong();
                TrackerStatus status = TrackerStatus.valueOf(input.readUTF());
                hasScore = input.readBoolean();
                OptionalDouble score = hasScore ? OptionalDouble.of(input.readDouble()) : OptionalDouble.empty();
                Optional<LocalDate> start = readOptional(input).map(LocalDate::parse);
                Optional<LocalDate> finish = readOptional(input).map(LocalDate::parse);
                boolean privateEntry = input.readBoolean();
                Optional<URI> uri = readOptional(input).map(URI::create);
                TrackerEntry entry = new TrackerEntry(
                        itemId, trackerId, remoteId, title, progress, total, status, score,
                        start, finish, privateEntry, uri, Instant.parse(input.readUTF()));
                if (unique.putIfAbsent(Key.of(entry), entry) != null) {
                    throw new BackupCodecException("Tracking section contains duplicate entries");
                }
                decoded.add(entry);
            }
            if (input.read() != -1) {
                throw new BackupCodecException("Unexpected trailing tracking data");
            }
            return List.copyOf(decoded);
        } catch (EOFException exception) {
            throw new BackupCodecException("Truncated tracking section", exception);
        } catch (IOException | IllegalArgumentException exception) {
            throw new BackupCodecException("Invalid tracking section", exception);
        }
    }

    private static void writeOptional(DataOutputStream output, Optional<String> value) throws IOException {
        output.writeBoolean(value.isPresent());
        if (value.isPresent()) {
            output.writeUTF(value.orElseThrow());
        }
    }

    private static Optional<String> readOptional(DataInputStream input) throws IOException {
        return input.readBoolean() ? Optional.of(input.readUTF()) : Optional.empty();
    }

    private record Key(LibraryItemId itemId, TrackerId trackerId) {
        private static Key of(TrackerEntry entry) {
            return new Key(entry.libraryItemId(), entry.trackerId());
        }
    }

    private static final class Restore implements PreparedBackupRestore {
        private final DefaultTrackerService service;
        private final List<TrackerEntry> before;
        private final List<TrackerEntry> replacement;
        private boolean committed;

        private Restore(
                DefaultTrackerService service,
                List<TrackerEntry> before,
                List<TrackerEntry> replacement) {
            this.service = service;
            this.before = before;
            this.replacement = replacement;
        }

        @Override
        public void commit() {
            service.replaceAll(replacement);
            committed = true;
        }

        @Override
        public void rollback() {
            if (committed) {
                service.replaceAll(before);
                committed = false;
            }
        }
    }
}
