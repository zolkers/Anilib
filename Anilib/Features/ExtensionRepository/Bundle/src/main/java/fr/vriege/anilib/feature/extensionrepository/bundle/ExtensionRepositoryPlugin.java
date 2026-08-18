package fr.vriege.anilib.feature.extensionrepository.bundle;

import fr.vriege.anilib.feature.extensionrepository.ExtensionRepositoryCapabilities;
import fr.vriege.anilib.feature.extensionrepository.runtime.DefaultExtensionRepositoryService;
import fr.vriege.anilib.feature.extensionrepository.runtime.FileExtensionRepositoryStore;
import fr.vriege.anilib.feature.network.NetworkCapabilities;
import fr.vriege.anilib.framework.http.AnilibHttpClient;
import fr.vriege.anilib.foundation.component.ComponentDescriptor;
import fr.vriege.anilib.kernel.AnilibPlugin;
import fr.vriege.anilib.kernel.PluginInstallationContext;
import fr.vriege.anilib.kernel.PluginManifest;

import java.nio.file.Path;
import java.util.Objects;

/** Composition unit for user-managed Aniyomi-compatible extension repositories. */
public final class ExtensionRepositoryPlugin implements AnilibPlugin {
    private static final PluginManifest MANIFEST = PluginManifest.builder(
                    ComponentDescriptor.of(
                            "feature.extension-repository",
                            "Extension repositories",
                            "1.0.0"))
            .requires(NetworkCapabilities.HTTP_CLIENT)
            .provides(ExtensionRepositoryCapabilities.SERVICE)
            .build();

    private final Path repositoryFile;

    public ExtensionRepositoryPlugin(Path repositoryFile) {
        this.repositoryFile = Objects.requireNonNull(repositoryFile, "repositoryFile must not be null")
                .toAbsolutePath()
                .normalize();
    }

    @Override
    public PluginManifest manifest() {
        return MANIFEST;
    }

    @Override
    public void install(PluginInstallationContext context) {
        AnilibHttpClient client = context.require(NetworkCapabilities.HTTP_CLIENT);
        context.publish(
                ExtensionRepositoryCapabilities.SERVICE,
                new DefaultExtensionRepositoryService(
                        new FileExtensionRepositoryStore(repositoryFile),
                        client));
    }
}
