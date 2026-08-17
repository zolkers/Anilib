package fr.vriege.anilib.feature.library.ui;

import fr.vriege.anilib.kernel.CapabilityKey;

/** Stable presentation capabilities published by the Library Bundle. */
public final class LibraryUiCapabilities {
    public static final CapabilityKey<LibraryPresentation> PRESENTATION =
            CapabilityKey.of("feature.library.presentation", LibraryPresentation.class);

    private LibraryUiCapabilities() {
    }
}
