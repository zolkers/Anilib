package fr.vriege.anilib.framework.backup;

/**
 * Encodes, validates, and transactionally restores one feature-owned backup
 * section.
 *
 * <p>Each feature remains the authority for its own persistent format. A codec
 * publishes a stable {@link #sectionId()}, a current format version, and the
 * operations needed by a backup coordinator. Unknown sections can be skipped
 * without loading their owning feature, while known sections are decoded and
 * validated by their codec before any state is changed.</p>
 *
 * <p>Inspection and restore preparation must accept the version stored beside
 * the payload rather than assuming {@link #currentVersion()}. Implementations
 * may support older versions or reject unsupported and malformed data with
 * {@link BackupCodecException}.</p>
 *
 * @see BackupSectionData
 * @see BackupSectionDetails
 * @see PreparedBackupRestore
 */
public interface BackupSectionCodec {
    /**
     * Returns the stable identity of the section owned by this codec.
     *
     * @return the non-null persistent section identifier
     */
    BackupSectionId sectionId();

    /**
     * Returns the user-facing name of this section.
     *
     * @return a non-blank display name
     */
    String displayName();

    /**
     * Returns the format version written by {@link #exportSection()}.
     *
     * @return a non-negative current format version
     */
    int currentVersion();

    /**
     * Encodes the current feature state as an immutable section snapshot.
     *
     * @return the encoded payload and its logical entry count
     * @throws BackupCodecException if the current state cannot be encoded
     */
    BackupSectionData exportSection();

    /**
     * Validates and describes an encoded section without changing feature
     * state.
     *
     * @param version the non-negative format version stored in the archive
     * @param payload the encoded section bytes
     * @return details decoded from and consistent with the payload
     * @throws NullPointerException if {@code payload} is {@code null}
     * @throws BackupCodecException if the version is unsupported or the
     *                              payload is invalid
     *
     * @implSpec This operation must not mutate persistent or observable feature
     * state.
     */
    BackupSectionDetails inspect(int version, byte[] payload);

    /**
     * Validates a section and prepares its restoration without committing any
     * state change.
     *
     * <p>The returned object captures both the proposed replacement or merge
     * and enough prior state to undo a later successful commit. Coordinators
     * prepare every known section before committing them in order.</p>
     *
     * @param version the non-negative format version stored in the archive
     * @param payload the encoded section bytes
     * @return a non-null prepared transactional restore
     * @throws NullPointerException if {@code payload} is {@code null}
     * @throws BackupCodecException if the version is unsupported or the
     *                              payload is invalid
     *
     * @implSpec This operation must fully validate the section and must not
     * mutate persistent or observable feature state.
     */
    PreparedBackupRestore prepareRestore(int version, byte[] payload);
}
