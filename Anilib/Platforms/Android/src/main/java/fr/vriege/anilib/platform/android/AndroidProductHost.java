package fr.vriege.anilib.platform.android;

import fr.vriege.anilib.configuration.standard.StandardAnilib;
import fr.vriege.anilib.kernel.CapabilityKey;
import fr.vriege.anilib.kernel.StartedAnilib;

import java.nio.file.Path;

/**
 * Android-lifecycle seam that stays free of SDK types.
 *
 * <p>The APK module calls {@link #start()} and {@link #stop()} from its Android
 * lifecycle and renders narrow capabilities. Android types must not be added
 * to this shared host.</p>
 */
public final class AndroidProductHost implements AutoCloseable {
    private final Path dataDirectory;
    private StartedAnilib application;

    public AndroidProductHost(Path dataDirectory) {
        this.dataDirectory = dataDirectory.toAbsolutePath().normalize();
    }

    public synchronized void start() {
        if (application != null) {
            throw new IllegalStateException("Android Anilib product is already started");
        }
        application = StandardAnilib.start(dataDirectory);
    }

    public synchronized <T> T capability(CapabilityKey<T> key) {
        if (application == null) {
            throw new IllegalStateException("Android Anilib product is not started");
        }
        return application.capability(key);
    }

    public synchronized int componentCount() {
        if (application == null) {
            throw new IllegalStateException("Android Anilib product is not started");
        }
        return application.components().size();
    }

    public synchronized void stop() {
        if (application != null) {
            application.close();
            application = null;
        }
    }

    @Override
    public void close() {
        stop();
    }
}
