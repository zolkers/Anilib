package fr.vriege.anilib.feature.tracker;

import fr.vriege.anilib.foundation.component.ComponentDescriptor;

import java.util.Objects;
import java.util.Set;

/** Host-reviewed identity and permission declaration for one tracker bundle. */
public record TrackerExtensionManifest(
        ComponentDescriptor component,
        TrackerId trackerId,
        Set<TrackerPermission> permissions,
        Set<TrackerNetworkOrigin> networkOrigins) {
    public TrackerExtensionManifest {
        Objects.requireNonNull(component, "component must not be null");
        Objects.requireNonNull(trackerId, "trackerId must not be null");
        permissions = Set.copyOf(permissions);
        networkOrigins = Set.copyOf(networkOrigins);
        boolean network = permissions.contains(TrackerPermission.NETWORK);
        if (network != !networkOrigins.isEmpty()) {
            throw new IllegalArgumentException("NETWORK permission and origins must be declared together");
        }
    }

    public static TrackerExtensionManifest offline(ComponentDescriptor component, TrackerId trackerId) {
        return new TrackerExtensionManifest(component, trackerId, Set.of(), Set.of());
    }
}
