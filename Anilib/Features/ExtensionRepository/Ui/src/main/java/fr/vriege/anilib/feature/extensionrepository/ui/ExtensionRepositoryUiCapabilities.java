package fr.vriege.anilib.feature.extensionrepository.ui;

import fr.vriege.anilib.kernel.CapabilityKey;

/** Presentation capability published with the extension-repository Bundle. */
public final class ExtensionRepositoryUiCapabilities {
    public static final CapabilityKey<ExtensionRepositoryPresentation> PRESENTATION = CapabilityKey.of(
            "feature.extension-repository.presentation",
            ExtensionRepositoryPresentation.class);

    private ExtensionRepositoryUiCapabilities() {
    }
}
