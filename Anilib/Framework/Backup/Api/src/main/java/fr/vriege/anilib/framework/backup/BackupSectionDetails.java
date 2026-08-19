package fr.vriege.anilib.framework.backup;

import fr.vriege.anilib.foundation.validation.Preconditions;

/**
 * Validated, user-presentable metadata decoded from a backup section.
 *
 * <p>A backup coordinator compares these values with the enclosing archive
 * metadata before allowing restoration. A codec must therefore report the
 * exact section identifier, encoded version, and logical entry count observed
 * in the payload.</p>
 *
 * @param id          the stable section identity
 * @param displayName the non-blank user-facing section name
 * @param version     the non-negative encoded format version
 * @param entryCount  the non-negative number of logical entries
 */
public record BackupSectionDetails(
        BackupSectionId id,
        String displayName,
        int version,
        int entryCount) {
    /**
     * Creates validated section details.
     *
     * @throws NullPointerException if {@code id} or {@code displayName} is
     *                              {@code null}
     * @throws IllegalArgumentException if {@code displayName} is blank or if
     *                                  {@code version} or {@code entryCount} is
     *                                  negative
     */
    public BackupSectionDetails {
        Preconditions.requireNonNull(id, "id");
        displayName = Preconditions.requireNonBlank(displayName, "displayName");
        if (version < 0) {
            throw new IllegalArgumentException("version must not be negative");
        }
        if (entryCount < 0) {
            throw new IllegalArgumentException("entryCount must not be negative");
        }
    }
}
