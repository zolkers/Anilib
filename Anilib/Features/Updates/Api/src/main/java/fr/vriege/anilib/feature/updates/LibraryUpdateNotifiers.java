package fr.vriege.anilib.feature.updates;

/** Built-in headless notifier used by tests and non-graphical hosts. */
public final class LibraryUpdateNotifiers {
    private static final LibraryUpdateNotifier SILENT = new LibraryUpdateNotifier() {
        @Override
        public boolean available() {
            return false;
        }

        @Override
        public void publish(LibraryUpdateNotification notification) {
            // Intentionally silent while retaining the explicit platform boundary.
        }
    };

    private LibraryUpdateNotifiers() {
    }

    public static LibraryUpdateNotifier silent() {
        return SILENT;
    }
}
