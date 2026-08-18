package fr.vriege.anilib.feature.source;

import fr.vriege.anilib.kernel.CapabilityKey;

public final class SourceCapabilities {
    public static final CapabilityKey<SourceRegistry> REGISTRY =
            CapabilityKey.of("feature.source.registry", SourceRegistry.class);
    public static final CapabilityKey<SourceRegistrar> REGISTRAR =
            CapabilityKey.of("feature.source.registrar", SourceRegistrar.class);

    private SourceCapabilities() {
    }
}
