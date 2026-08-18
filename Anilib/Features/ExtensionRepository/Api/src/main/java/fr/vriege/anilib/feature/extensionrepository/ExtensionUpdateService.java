package fr.vriege.anilib.feature.extensionrepository;

import java.util.List;

/** Shared update channel for installed portable sources on Android and desktop. */
public interface ExtensionUpdateService {
    List<ExtensionUpdateCandidate> availableUpdates();

    List<ExtensionUpdateCandidate> checkForUpdates();

    ExtensionUpdateResult updateAllAvailable();

    boolean automaticUpdatesEnabled();

    void setAutomaticUpdatesEnabled(boolean enabled);

    AutoCloseable observe(Runnable listener);
}
