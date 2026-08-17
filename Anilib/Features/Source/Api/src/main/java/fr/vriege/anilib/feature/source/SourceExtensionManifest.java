package fr.vriege.anilib.feature.source;

import fr.vriege.anilib.foundation.component.ComponentDescriptor;
import fr.vriege.anilib.foundation.validation.Preconditions;

import java.util.Set;

/** Side-effect-free identity and permission request for one explicitly selected source Bundle. */
public record SourceExtensionManifest(
        ComponentDescriptor component,
        SourceId sourceId,
        Set<SourcePermission> permissions,
        Set<SourceNetworkOrigin> networkOrigins) {
    public SourceExtensionManifest {
        Preconditions.requireNonNull(component, "component");
        Preconditions.requireNonNull(sourceId, "sourceId");
        permissions = Set.copyOf(Preconditions.requireNonNull(permissions, "permissions"));
        networkOrigins = Set.copyOf(Preconditions.requireNonNull(networkOrigins, "networkOrigins"));
        boolean network = permissions.contains(SourcePermission.NETWORK);
        boolean cleartext = permissions.contains(SourcePermission.CLEARTEXT_NETWORK);
        boolean hasCleartextOrigin = networkOrigins.stream()
                .anyMatch(origin -> origin.scheme().equals("http"));
        if (network != !networkOrigins.isEmpty()) {
            throw new IllegalArgumentException("NETWORK permission requires at least one exact origin");
        }
        if (cleartext && !network) {
            throw new IllegalArgumentException("CLEARTEXT_NETWORK permission requires NETWORK");
        }
        if (cleartext != hasCleartextOrigin) {
            throw new IllegalArgumentException(
                    "CLEARTEXT_NETWORK permission must match the presence of an HTTP origin");
        }
    }

    public static SourceExtensionManifest offline(
            ComponentDescriptor component,
            SourceId sourceId) {
        return new SourceExtensionManifest(component, sourceId, Set.of(), Set.of());
    }

    public static SourceExtensionManifest networked(
            ComponentDescriptor component,
            SourceId sourceId,
            Set<SourceNetworkOrigin> origins) {
        Set<SourceNetworkOrigin> values = Set.copyOf(origins);
        boolean cleartext = values.stream().anyMatch(origin -> origin.scheme().equals("http"));
        Set<SourcePermission> permissions = cleartext
                ? Set.of(SourcePermission.NETWORK, SourcePermission.CLEARTEXT_NETWORK)
                : Set.of(SourcePermission.NETWORK);
        return new SourceExtensionManifest(component, sourceId, permissions, values);
    }
}
