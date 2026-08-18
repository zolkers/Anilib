package fr.vriege.anilib.feature.tracker;

import java.util.Objects;

public record InstalledTrackerExtension(
        TrackerExtensionManifest manifest,
        TrackerDescriptor descriptor) {
    public InstalledTrackerExtension {
        Objects.requireNonNull(manifest, "manifest must not be null");
        Objects.requireNonNull(descriptor, "descriptor must not be null");
    }
}
