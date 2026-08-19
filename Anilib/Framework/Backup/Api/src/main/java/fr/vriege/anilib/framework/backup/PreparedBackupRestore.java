package fr.vriege.anilib.framework.backup;

/**
 * A validated, not-yet-committed change for one backup section.
 *
 * <p>Prepared restores allow a coordinator to validate all known sections
 * before any feature is mutated. If every preparation succeeds, the coordinator
 * commits sections in deterministic order. When a later commit fails, it calls
 * {@link #rollback()} on earlier successful commits in reverse order and then
 * {@link #close()} on every prepared restore.</p>
 *
 * <p>Implementations may retain temporary resources until closed. They should
 * not retain the caller's payload array without making a defensive copy.</p>
 *
 * @see BackupSectionCodec#prepareRestore(int, byte[])
 */
public interface PreparedBackupRestore extends AutoCloseable {
    /**
     * Atomically applies the prepared feature-state change.
     *
     * @throws BackupCodecException if the prepared change cannot be committed
     *
     * @implSpec A normal return marks this restore as committed and eligible
     * for {@link #rollback()}. If this method throws, it must not leave a
     * partially applied state.
     */
    void commit();

    /**
     * Reverts a previously successful {@link #commit()}.
     *
     * @throws BackupCodecException if the prior state cannot be restored
     *
     * @implSpec This method must restore the state captured during preparation.
     * Coordinators invoke it only after {@code commit()} returned normally.
     */
    void rollback();

    /**
     * Releases temporary resources retained by this prepared restore.
     *
     * <p>The default implementation performs no action.</p>
     */
    @Override
    default void close() {
    }
}
