package fr.vriege.anilib.feature.library;

import fr.vriege.anilib.kernel.CapabilityKey;

/** Stable capabilities published by the Library Bundle. */
public final class LibraryCapabilities {
    public static final CapabilityKey<LibraryCatalog> CATALOG =
            CapabilityKey.of("feature.library.catalog", LibraryCatalog.class);

    private LibraryCapabilities() {
    }
}
