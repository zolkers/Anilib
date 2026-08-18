package fr.vriege.anilib.feature.extensionrepository.bundle;

import fr.vriege.anilib.feature.extensionrepository.ExtensionBundleLoadFailure;
import fr.vriege.anilib.feature.extensionrepository.ExtensionRepositoryCapabilities;
import fr.vriege.anilib.feature.extensionrepository.runtime.DefaultExtensionInstallationService;
import fr.vriege.anilib.feature.extensionrepository.runtime.DefaultExtensionRepositoryService;
import fr.vriege.anilib.feature.extensionrepository.runtime.DefaultExtensionUpdateService;
import fr.vriege.anilib.feature.extensionrepository.runtime.FileExtensionRepositoryStore;
import fr.vriege.anilib.feature.extensionrepository.runtime.FileExtensionUpdatePolicyStore;
import fr.vriege.anilib.feature.extensionrepository.ui.DefaultExtensionRepositoryPresentation;
import fr.vriege.anilib.feature.extensionrepository.ui.ExtensionRepositoryUiCapabilities;
import fr.vriege.anilib.feature.network.NetworkCapabilities;
import fr.vriege.anilib.framework.http.AnilibHttpClient;
import fr.vriege.anilib.foundation.component.ComponentDescriptor;
import fr.vriege.anilib.kernel.AnilibPlugin;
import fr.vriege.anilib.kernel.PluginInstallationContext;
import fr.vriege.anilib.kernel.PluginManifest;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

public final class ExtensionRepositoryPlugin implements AnilibPlugin {
    private static final PluginManifest MANIFEST = PluginManifest.builder(
                    ComponentDescriptor.of(
                            "feature.extension-repository",
                            "Extension repositories",
                            "1.0.0"))
            .requires(NetworkCapabilities.HTTP_CLIENT)
            .provides(ExtensionRepositoryCapabilities.SERVICE)
            .provides(ExtensionRepositoryCapabilities.INSTALLATION)
            .provides(ExtensionRepositoryCapabilities.UPDATES)
            .provides(ExtensionRepositoryUiCapabilities.PRESENTATION)
            .build();

    private final Path repositoryFile;
    private final List<ExtensionBundleLoadFailure> loadFailures;

    public ExtensionRepositoryPlugin(Path repositoryFile) {
        this(repositoryFile, List.of());
    }

    public ExtensionRepositoryPlugin(
            Path repositoryFile,
            List<ExtensionBundleLoadFailure> loadFailures) {
        this.repositoryFile = Objects.requireNonNull(repositoryFile, "repositoryFile must not be null")
                .toAbsolutePath()
                .normalize();
        this.loadFailures = List.copyOf(Objects.requireNonNull(loadFailures, "loadFailures must not be null"));
    }

    @Override
    public PluginManifest manifest() {
        return MANIFEST;
    }

    @Override
    public void install(PluginInstallationContext context) {
        AnilibHttpClient client = context.require(NetworkCapabilities.HTTP_CLIENT);
        DefaultExtensionRepositoryService service = new DefaultExtensionRepositoryService(
                new FileExtensionRepositoryStore(repositoryFile),
                client);
        DefaultExtensionInstallationService installation = new DefaultExtensionInstallationService(
                repositoryFile.resolveSibling("extensions"),
                client,
                loadFailures);
        DefaultExtensionUpdateService updates = new DefaultExtensionUpdateService(
                service,
                installation,
                new FileExtensionUpdatePolicyStore(repositoryFile.resolveSibling("extension-updates.properties")));
        DefaultExtensionRepositoryPresentation presentation = new DefaultExtensionRepositoryPresentation(
                service,
                installation,
                updates);
        context.own(updates);
        context.own(presentation);
        context.publish(ExtensionRepositoryCapabilities.SERVICE, service);
        context.publish(ExtensionRepositoryCapabilities.INSTALLATION, installation);
        context.publish(ExtensionRepositoryCapabilities.UPDATES, updates);
        context.publish(ExtensionRepositoryUiCapabilities.PRESENTATION, presentation);
    }
}
