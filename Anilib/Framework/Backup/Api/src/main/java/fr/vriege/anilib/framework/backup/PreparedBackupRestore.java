package fr.vriege.anilib.framework.backup;

/** Validated restore step that can commit atomically and compensate after a later failure. */
public interface PreparedBackupRestore extends AutoCloseable {
    void commit();

    void rollback();

    @Override
    default void close() {
    }
}
