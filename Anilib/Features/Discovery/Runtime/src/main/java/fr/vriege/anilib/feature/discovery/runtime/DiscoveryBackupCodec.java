package fr.vriege.anilib.feature.discovery.runtime;

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
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/** Discovery-owned codec for source preferences, including currently missing extensions. */
public final class DiscoveryBackupCodec implements BackupSectionCodec {
    private static final BackupSectionId SECTION_ID = BackupSectionId.of("source-preferences");
    private static final int MAGIC = 0x44535046;
    private static final int CURRENT_VERSION = 1;
    private static final int MAXIMUM_ENTRIES = 1_000_000;
    private final FileSourcePreferenceStore preferences;

    public DiscoveryBackupCodec(FileSourcePreferenceStore preferences) {
        this.preferences = Objects.requireNonNull(preferences, "preferences must not be null");
    }

    @Override
    public BackupSectionId sectionId() {
        return SECTION_ID;
    }

    @Override
    public String displayName() {
        return "Source preferences";
    }

    @Override
    public int currentVersion() {
        return CURRENT_VERSION;
    }

    @Override
    public BackupSectionData exportSection() {
        Map<String, String> values = preferences.snapshot();
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream output = new DataOutputStream(bytes)) {
                output.writeInt(MAGIC);
                output.writeInt(CURRENT_VERSION);
                output.writeInt(values.size());
                for (Map.Entry<String, String> entry : new TreeMap<>(values).entrySet()) {
                    output.writeUTF(entry.getKey());
                    output.writeUTF(entry.getValue());
                }
            }
            return new BackupSectionData(bytes.toByteArray(), values.size());
        } catch (IOException exception) {
            throw new BackupCodecException("Unable to encode source preferences", exception);
        }
    }

    @Override
    public BackupSectionDetails inspect(int version, byte[] payload) {
        Map<String, String> values = decode(version, payload);
        return new BackupSectionDetails(SECTION_ID, displayName(), version, values.size());
    }

    @Override
    public PreparedBackupRestore prepareRestore(int version, byte[] payload) {
        Map<String, String> before = preferences.snapshot();
        Map<String, String> merged = new LinkedHashMap<>(before);
        merged.putAll(decode(version, payload));
        return new PreferenceRestore(preferences, before, Map.copyOf(merged));
    }

    private static Map<String, String> decode(int version, byte[] payload) {
        if (version != CURRENT_VERSION) {
            throw new BackupCodecException("Unsupported source preference backup version: " + version);
        }
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(
                Objects.requireNonNull(payload, "payload must not be null")))) {
            if (input.readInt() != MAGIC || input.readInt() != version) {
                throw new BackupCodecException("Source preference section signature or version is invalid");
            }
            int count = input.readInt();
            if (count < 0 || count > MAXIMUM_ENTRIES) {
                throw new BackupCodecException("Invalid source preference count: " + count);
            }
            Map<String, String> values = new LinkedHashMap<>();
            for (int index = 0; index < count; index++) {
                String key = input.readUTF();
                String value = input.readUTF();
                if (key.isBlank() || values.putIfAbsent(key, value) != null) {
                    throw new BackupCodecException("Source preference keys must be non-blank and unique");
                }
            }
            if (input.read() != -1) {
                throw new BackupCodecException("Unexpected trailing source preference data");
            }
            return Map.copyOf(values);
        } catch (EOFException exception) {
            throw new BackupCodecException("Truncated source preference section", exception);
        } catch (IOException exception) {
            throw new BackupCodecException("Invalid source preference section", exception);
        }
    }

    private static final class PreferenceRestore implements PreparedBackupRestore {
        private final FileSourcePreferenceStore preferences;
        private final Map<String, String> before;
        private final Map<String, String> replacement;
        private boolean committed;

        private PreferenceRestore(
                FileSourcePreferenceStore preferences,
                Map<String, String> before,
                Map<String, String> replacement) {
            this.preferences = preferences;
            this.before = before;
            this.replacement = replacement;
        }

        @Override
        public void commit() {
            preferences.replaceAll(replacement);
            committed = true;
        }

        @Override
        public void rollback() {
            if (committed) {
                preferences.replaceAll(before);
                committed = false;
            }
        }
    }
}
