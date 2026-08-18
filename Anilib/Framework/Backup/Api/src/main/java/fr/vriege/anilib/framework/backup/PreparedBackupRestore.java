package fr.vriege.anilib.framework.backup;

public interface PreparedBackupRestore extends AutoCloseable {
    void commit();

    void rollback();

    @Override
    default void close() {
    }
}
