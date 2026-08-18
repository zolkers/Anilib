package fr.vriege.anilib.feature.updates;

/** Narrow platform boundary for Android and desktop system notifications. */
public interface LibraryUpdateNotifier extends AutoCloseable {
    boolean available();

    void publish(LibraryUpdateNotification notification);

    @Override
    default void close() {
    }
}
