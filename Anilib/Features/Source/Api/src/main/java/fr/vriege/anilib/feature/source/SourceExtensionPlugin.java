package fr.vriege.anilib.feature.source;

import fr.vriege.anilib.foundation.component.ComponentDescriptor;
import fr.vriege.anilib.foundation.validation.Preconditions;
import fr.vriege.anilib.kernel.AnilibPlugin;
import fr.vriege.anilib.kernel.PluginInstallationContext;
import fr.vriege.anilib.kernel.PluginManifest;

/** Generic Bundle adapter for one explicitly selected source implementation. */
public final class SourceExtensionPlugin implements AnilibPlugin {
    private final PluginManifest manifest;
    private final Source source;

    public SourceExtensionPlugin(ComponentDescriptor component, Source source) {
        this.source = Preconditions.requireNonNull(source, "source");
        manifest = PluginManifest.builder(Preconditions.requireNonNull(component, "component"))
                .requires(SourceCapabilities.REGISTRAR)
                .build();
    }

    @Override
    public PluginManifest manifest() {
        return manifest;
    }

    @Override
    public void install(PluginInstallationContext context) {
        SourceRegistrar registrar = context.require(SourceCapabilities.REGISTRAR);
        context.own(registrar.register(source));
    }
}
