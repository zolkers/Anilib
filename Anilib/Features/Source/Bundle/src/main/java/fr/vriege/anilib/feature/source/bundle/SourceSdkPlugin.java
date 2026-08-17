package fr.vriege.anilib.feature.source.bundle;

import fr.vriege.anilib.feature.source.SourceCapabilities;
import fr.vriege.anilib.feature.source.runtime.DefaultSourceRegistry;
import fr.vriege.anilib.foundation.component.ComponentDescriptor;
import fr.vriege.anilib.kernel.AnilibPlugin;
import fr.vriege.anilib.kernel.PluginInstallationContext;
import fr.vriege.anilib.kernel.PluginManifest;

/** Composition unit publishing the Source SDK registry and registrar. */
public final class SourceSdkPlugin implements AnilibPlugin {
    private static final PluginManifest MANIFEST = PluginManifest.builder(
                    ComponentDescriptor.of("feature.source", "Source SDK", "1.0.0"))
            .provides(SourceCapabilities.REGISTRY)
            .provides(SourceCapabilities.REGISTRAR)
            .build();

    public SourceSdkPlugin() {
    }

    @Override
    public PluginManifest manifest() {
        return MANIFEST;
    }

    @Override
    public void install(PluginInstallationContext context) {
        DefaultSourceRegistry registry = context.own(new DefaultSourceRegistry());
        context.publish(SourceCapabilities.REGISTRY, registry);
        context.publish(SourceCapabilities.REGISTRAR, registry);
    }
}
