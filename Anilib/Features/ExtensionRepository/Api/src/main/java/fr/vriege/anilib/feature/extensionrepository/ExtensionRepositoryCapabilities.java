package fr.vriege.anilib.feature.extensionrepository;

import fr.vriege.anilib.kernel.CapabilityKey;

/** Typed capabilities published by the removable extension-repository Bundle. */
public final class ExtensionRepositoryCapabilities {
    public static final CapabilityKey<ExtensionRepositoryService> SERVICE = CapabilityKey.of(
            "feature.extension-repository.service",
            ExtensionRepositoryService.class);

    private ExtensionRepositoryCapabilities() {
    }
}
