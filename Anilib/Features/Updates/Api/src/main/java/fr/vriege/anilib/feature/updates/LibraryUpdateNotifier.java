package fr.vriege.anilib.feature.updates;

public interface LibraryUpdateNotifier extends AutoCloseable {
    boolean available();

    void publish(LibraryUpdateNotification notification);

    @Override
    default void close() {
    }
}
