package fr.vriege.anilib.framework.backup;

import java.util.Objects;

public record BackupSectionData(byte[] payload, int entryCount) {
    public BackupSectionData {
        payload = Objects.requireNonNull(payload, "payload must not be null").clone();
        if (entryCount < 0) {
            throw new IllegalArgumentException("entryCount must not be negative");
        }
    }

    @Override
    public byte[] payload() {
        return payload.clone();
    }
}
