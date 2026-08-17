package fr.vriege.anilib.platform.android;

import fr.vriege.anilib.configuration.standard.StandardAnilib;
import fr.vriege.anilib.kernel.CapabilityKey;
import fr.vriege.anilib.kernel.StartedAnilib;

/**
 * Android-lifecycle seam that stays free of SDK types.
 *
 * <p>A future APK module will call {@link #start()} and {@link #stop()} from its
 * Android lifecycle and render narrow capabilities. Android types must not be
 * added to this shared host.</p>
 */
public final class AndroidProductHost implements AutoCloseable {
    private StartedAnilib application;

    public AndroidProductHost() {
    }

    public synchronized void start() {
        if (application != null) {
            throw new IllegalStateException("Android Anilib product is already started");
        }
        application = StandardAnilib.start();
    }

    public synchronized <T> T capability(CapabilityKey<T> key) {
        if (application == null) {
            throw new IllegalStateException("Android Anilib product is not started");
        }
        return application.capability(key);
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
