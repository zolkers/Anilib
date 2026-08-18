package fr.vriege.anilib.feature.localsource.bundle;

import fr.vriege.anilib.feature.localsource.LocalSourceCapabilities;
import fr.vriege.anilib.feature.localsource.runtime.FileSystemLocalContentSource;
import fr.vriege.anilib.feature.source.SourceCapabilities;
import fr.vriege.anilib.feature.source.SourceRegistrar;
import fr.vriege.anilib.foundation.component.ComponentDescriptor;
import fr.vriege.anilib.kernel.AnilibPlugin;
import fr.vriege.anilib.kernel.PluginInstallationContext;
import fr.vriege.anilib.kernel.PluginManifest;

import java.nio.file.Path;

public final class LocalSourcePlugin implements AnilibPlugin {
    private static final PluginManifest MANIFEST = PluginManifest.builder(
                    ComponentDescriptor.of("feature.local-source", "Local source", "0.1.0"))
            .provides(LocalSourceCapabilities.CONTENT)
            .requires(SourceCapabilities.REGISTRAR)
            .build();
    private final Path root;

    public LocalSourcePlugin(Path root) {
        this.root = root.toAbsolutePath().normalize();
    }

    @Override
    public PluginManifest manifest() {
        return MANIFEST;
    }

    @Override
    public void install(PluginInstallationContext context) {
        FileSystemLocalContentSource source = new FileSystemLocalContentSource(root);
        SourceRegistrar registrar = context.require(SourceCapabilities.REGISTRAR);
        context.own(registrar.register(source));
        context.publish(LocalSourceCapabilities.CONTENT, source);
    }
}
