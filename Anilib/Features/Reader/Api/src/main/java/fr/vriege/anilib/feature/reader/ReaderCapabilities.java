package fr.vriege.anilib.feature.reader;

import fr.vriege.anilib.kernel.CapabilityKey;

/** Stable reader capability published by the Reader Bundle. */
public final class ReaderCapabilities {
    public static final CapabilityKey<ReaderService> SERVICE =
            CapabilityKey.of("feature.reader.service", ReaderService.class);

    private ReaderCapabilities() {
    }
}
