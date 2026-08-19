package fr.vriege.anilib.framework.backup;

import java.util.Objects;

/**
 * The encoded payload and summary count exported for one backup section.
 *
 * <p>The byte array is defensively copied on construction and on access, so an
 * instance owns an immutable snapshot of the encoded feature state. The entry
 * count is archive metadata and should describe the logical number of items in
 * the payload.</p>
 *
 * @param payload    the encoded section bytes
 * @param entryCount the non-negative logical item count
 */
public record BackupSectionData(byte[] payload, int entryCount) {
    /**
     * Creates immutable exported section data.
     *
     * @throws NullPointerException if {@code payload} is {@code null}
     * @throws IllegalArgumentException if {@code entryCount} is negative
     */
    public BackupSectionData {
        payload = Objects.requireNonNull(payload, "payload must not be null").clone();
        if (entryCount < 0) {
            throw new IllegalArgumentException("entryCount must not be negative");
        }
    }

    /**
     * Returns a copy of the encoded payload.
     *
     * @return a new byte array containing the section payload
     */
    @Override
    public byte[] payload() {
        return payload.clone();
    }
}
