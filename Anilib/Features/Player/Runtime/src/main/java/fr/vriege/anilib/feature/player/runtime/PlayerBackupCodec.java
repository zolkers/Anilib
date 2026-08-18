package fr.vriege.anilib.feature.player.runtime;

import fr.vriege.anilib.feature.library.LibraryItemId;
import fr.vriege.anilib.feature.player.PlaybackState;
import fr.vriege.anilib.feature.source.SourceCatalogueItemId;
import fr.vriege.anilib.feature.source.SourceEpisodeId;
import fr.vriege.anilib.feature.source.SourceId;
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
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class PlayerBackupCodec implements BackupSectionCodec {
    private static final BackupSectionId SECTION_ID = BackupSectionId.of("playback-state");
    private static final int MAGIC = 0x504C4159;
    private static final int CURRENT_VERSION = 1;
    private static final int MAXIMUM_ENTRIES = 1_000_000;
    private static final Comparator<PlaybackState> ORDER = Comparator
            .comparing((PlaybackState state) -> state.libraryItemId().value())
            .thenComparing(state -> state.episodeId().itemId().sourceId())
            .thenComparing(state -> state.episodeId().itemId().value())
            .thenComparing(state -> state.episodeId().value());
    private final PlaybackStateStore states;

    public PlayerBackupCodec(DefaultPlayerService service) {
        states = Objects.requireNonNull(service, "service must not be null").stateStore();
    }

    @Override
    public BackupSectionId sectionId() {
        return SECTION_ID;
    }

    @Override
    public String displayName() {
        return "Playback state";
    }

    @Override
    public int currentVersion() {
        return CURRENT_VERSION;
    }

    @Override
    public BackupSectionData exportSection() {
        List<PlaybackState> snapshot = states.snapshot();
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream output = new DataOutputStream(bytes)) {
                output.writeInt(MAGIC);
                output.writeInt(CURRENT_VERSION);
                output.writeInt(snapshot.size());
                for (PlaybackState state : snapshot.stream().sorted(ORDER).toList()) {
                    write(output, state);
                }
            }
            return new BackupSectionData(bytes.toByteArray(), snapshot.size());
        } catch (IOException exception) {
            throw new BackupCodecException("Unable to encode playback state", exception);
        }
    }

    @Override
    public BackupSectionDetails inspect(int version, byte[] payload) {
        List<PlaybackState> decoded = decode(version, payload);
        return new BackupSectionDetails(SECTION_ID, displayName(), version, decoded.size());
    }

    @Override
    public PreparedBackupRestore prepareRestore(int version, byte[] payload) {
        List<PlaybackState> before = states.snapshot();
        Map<Key, PlaybackState> merged = new LinkedHashMap<>();
        before.forEach(state -> merged.put(Key.of(state), state));
        for (PlaybackState imported : decode(version, payload)) {
            merged.merge(Key.of(imported), imported, PlayerBackupCodec::newest);
        }
        return new PlaybackRestore(states, before, List.copyOf(merged.values()));
    }

    private static PlaybackState newest(PlaybackState current, PlaybackState imported) {
        return imported.updatedAt().isAfter(current.updatedAt()) ? imported : current;
    }

    private static void write(DataOutputStream output, PlaybackState state) throws IOException {
        output.writeUTF(state.libraryItemId().value());
        output.writeUTF(state.episodeId().itemId().sourceId().toString());
        output.writeUTF(state.episodeId().itemId().value());
        output.writeUTF(state.episodeId().value());
        output.writeLong(state.positionMillis());
        output.writeLong(state.durationMillis());
        output.writeBoolean(state.completed());
        output.writeUTF(state.updatedAt().toString());
    }

    private static List<PlaybackState> decode(int version, byte[] payload) {
        if (version != CURRENT_VERSION) {
            throw new BackupCodecException("Unsupported playback backup version: " + version);
        }
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(
                Objects.requireNonNull(payload, "payload must not be null")))) {
            if (input.readInt() != MAGIC || input.readInt() != version) {
                throw new BackupCodecException("Playback section signature or version is invalid");
            }
            int count = input.readInt();
            if (count < 0 || count > MAXIMUM_ENTRIES) {
                throw new BackupCodecException("Invalid playback state count: " + count);
            }
            List<PlaybackState> decoded = new ArrayList<>();
            Map<Key, PlaybackState> unique = new LinkedHashMap<>();
            for (int index = 0; index < count; index++) {
                LibraryItemId libraryItemId = new LibraryItemId(input.readUTF());
                SourceCatalogueItemId itemId = new SourceCatalogueItemId(
                        SourceId.of(input.readUTF()),
                        input.readUTF());
                PlaybackState state = new PlaybackState(
                        libraryItemId,
                        new SourceEpisodeId(itemId, input.readUTF()),
                        input.readLong(),
                        input.readLong(),
                        input.readBoolean(),
                        Instant.parse(input.readUTF()));
                if (unique.putIfAbsent(Key.of(state), state) != null) {
                    throw new BackupCodecException("Playback section contains duplicate states");
                }
                decoded.add(state);
            }
            if (input.read() != -1) {
                throw new BackupCodecException("Unexpected trailing playback state data");
            }
            return List.copyOf(decoded);
        } catch (EOFException exception) {
            throw new BackupCodecException("Truncated playback state section", exception);
        } catch (IOException | IllegalArgumentException exception) {
            throw new BackupCodecException("Invalid playback state section", exception);
        }
    }

    private record Key(LibraryItemId itemId, SourceEpisodeId episodeId) {
        private static Key of(PlaybackState state) {
            return new Key(state.libraryItemId(), state.episodeId());
        }
    }

    private static final class PlaybackRestore implements PreparedBackupRestore {
        private final PlaybackStateStore states;
        private final List<PlaybackState> before;
        private final List<PlaybackState> replacement;
        private boolean committed;

        private PlaybackRestore(
                PlaybackStateStore states,
                List<PlaybackState> before,
                List<PlaybackState> replacement) {
            this.states = states;
            this.before = before;
            this.replacement = replacement;
        }

        @Override
        public void commit() {
            states.replaceAll(replacement);
            committed = true;
        }

        @Override
        public void rollback() {
            if (committed) {
                states.replaceAll(before);
                committed = false;
            }
        }
    }
}
