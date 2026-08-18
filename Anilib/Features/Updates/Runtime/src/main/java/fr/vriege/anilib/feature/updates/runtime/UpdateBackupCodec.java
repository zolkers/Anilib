package fr.vriege.anilib.feature.updates.runtime;

import fr.vriege.anilib.feature.library.LibraryItemId;
import fr.vriege.anilib.feature.updates.LibraryUpdateEvent;
import fr.vriege.anilib.framework.backup.BackupSectionCodec;
import fr.vriege.anilib.framework.backup.BackupSectionData;
import fr.vriege.anilib.framework.backup.BackupSectionDetails;
import fr.vriege.anilib.framework.backup.BackupSectionId;
import fr.vriege.anilib.framework.backup.PreparedBackupRestore;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public final class UpdateBackupCodec implements BackupSectionCodec {
    private static final BackupSectionId SECTION_ID = BackupSectionId.of("library-updates");
    private static final int CURRENT_VERSION = 1;
    private final DefaultLibraryUpdateService service;
    private final LibraryUpdateStore store;

    public UpdateBackupCodec(DefaultLibraryUpdateService service) {
        this.service = Objects.requireNonNull(service, "service must not be null");
        this.store = service.stateStore();
    }

    @Override
    public BackupSectionId sectionId() {
        return SECTION_ID;
    }

    @Override
    public String displayName() {
        return "Library updates";
    }

    @Override
    public int currentVersion() {
        return CURRENT_VERSION;
    }

    @Override
    public BackupSectionData exportSection() {
        LibraryUpdateStore.State state = store.snapshot();
        return new BackupSectionData(store.exportState(), state.events().size());
    }

    @Override
    public BackupSectionDetails inspect(int version, byte[] payload) {
        LibraryUpdateStore.State state = decode(version, payload);
        return new BackupSectionDetails(SECTION_ID, displayName(), version, state.events().size());
    }

    @Override
    public PreparedBackupRestore prepareRestore(int version, byte[] payload) {
        LibraryUpdateStore.State before = store.snapshot();
        LibraryUpdateStore.State replacement = merge(before, decode(version, payload));
        return new Restore(service, before, replacement);
    }

    private static LibraryUpdateStore.State decode(int version, byte[] payload) {
        if (version != CURRENT_VERSION) {
            throw new fr.vriege.anilib.framework.backup.BackupCodecException(
                    "Unsupported library updates backup version: " + version);
        }
        return LibraryUpdateStore.decodeState(payload);
    }

    private static LibraryUpdateStore.State merge(
            LibraryUpdateStore.State current,
            LibraryUpdateStore.State imported) {
        Map<LibraryItemId, Set<String>> baselines = new LinkedHashMap<>();
        current.baselines().forEach((itemId, ids) -> baselines.put(itemId, new LinkedHashSet<>(ids)));
        imported.baselines().forEach((itemId, ids) ->
                baselines.computeIfAbsent(itemId, ignored -> new LinkedHashSet<>()).addAll(ids));
        Map<EventKey, LibraryUpdateEvent> events = new LinkedHashMap<>();
        current.events().forEach(event -> events.put(EventKey.of(event), event));
        imported.events().forEach(event -> events.merge(EventKey.of(event), event, UpdateBackupCodec::mergeEvent));
        Optional<java.time.Instant> lastRunAt = newest(current.lastRunAt(), imported.lastRunAt());
        return new LibraryUpdateStore.State(
                imported.policy(),
                baselines,
                List.copyOf(events.values()),
                lastRunAt);
    }

    private static LibraryUpdateEvent mergeEvent(LibraryUpdateEvent current, LibraryUpdateEvent imported) {
        LibraryUpdateEvent newest = imported.discoveredAt().isAfter(current.discoveredAt()) ? imported : current;
        return current.read() && imported.read() ? newest.markRead() : unread(newest);
    }

    private static LibraryUpdateEvent unread(LibraryUpdateEvent event) {
        return new LibraryUpdateEvent(
                event.libraryItemId(),
                event.libraryTitle(),
                event.kind(),
                event.sourceContentId(),
                event.contentTitle(),
                event.publishedAt(),
                event.discoveredAt(),
                false);
    }

    private static Optional<java.time.Instant> newest(
            Optional<java.time.Instant> first,
            Optional<java.time.Instant> second) {
        if (first.isEmpty()) {
            return second;
        }
        if (second.isEmpty()) {
            return first;
        }
        return first.orElseThrow().isAfter(second.orElseThrow()) ? first : second;
    }

    private record EventKey(LibraryItemId itemId, String contentId) {
        private static EventKey of(LibraryUpdateEvent event) {
            return new EventKey(event.libraryItemId(), event.sourceContentId());
        }
    }

    private static final class Restore implements PreparedBackupRestore {
        private final DefaultLibraryUpdateService service;
        private final LibraryUpdateStore.State before;
        private final LibraryUpdateStore.State replacement;
        private boolean committed;

        private Restore(
                DefaultLibraryUpdateService service,
                LibraryUpdateStore.State before,
                LibraryUpdateStore.State replacement) {
            this.service = service;
            this.before = before;
            this.replacement = replacement;
        }

        @Override
        public void commit() {
            service.replaceState(replacement);
            committed = true;
        }

        @Override
        public void rollback() {
            if (committed) {
                service.replaceState(before);
                committed = false;
            }
        }
    }
}
