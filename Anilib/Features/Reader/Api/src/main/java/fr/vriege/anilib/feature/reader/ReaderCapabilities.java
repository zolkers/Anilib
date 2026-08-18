package fr.vriege.anilib.feature.reader;

import fr.vriege.anilib.kernel.CapabilityKey;

public final class ReaderCapabilities {
    public static final CapabilityKey<ReaderService> SERVICE =
            CapabilityKey.of("feature.reader.service", ReaderService.class);
    public static final CapabilityKey<ReaderContentRegistrar> CONTENT_REGISTRAR =
            CapabilityKey.of("feature.reader.content-registrar", ReaderContentRegistrar.class);

    private ReaderCapabilities() {
    }
}
