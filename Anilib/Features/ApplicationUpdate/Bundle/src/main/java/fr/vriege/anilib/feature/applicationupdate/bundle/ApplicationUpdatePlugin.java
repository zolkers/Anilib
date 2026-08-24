package fr.vriege.anilib.feature.applicationupdate.bundle;

import fr.vriege.anilib.feature.applicationupdate.ApplicationPlatform;
import fr.vriege.anilib.feature.applicationupdate.ApplicationUpdateCapabilities;
import fr.vriege.anilib.feature.applicationupdate.ApplicationVersion;
import fr.vriege.anilib.feature.applicationupdate.runtime.GitHubApplicationUpdateService;
import fr.vriege.anilib.feature.applicationupdate.runtime.FileApplicationUpdateChannelStore;
import fr.vriege.anilib.feature.applicationupdate.ui.ApplicationUpdateUiCapabilities;
import fr.vriege.anilib.feature.applicationupdate.ui.DefaultApplicationUpdatePresentation;
import fr.vriege.anilib.feature.network.NetworkCapabilities;
import fr.vriege.anilib.foundation.component.ComponentDescriptor;
import fr.vriege.anilib.framework.http.AnilibHttpClient;
import fr.vriege.anilib.kernel.AnilibPlugin;
import fr.vriege.anilib.kernel.PluginInstallationContext;
import fr.vriege.anilib.kernel.PluginManifest;

import java.net.URI;
import java.nio.file.Path;
import java.util.Objects;

public final class ApplicationUpdatePlugin implements AnilibPlugin {
    private static final URI RELEASE_ENDPOINT =
            URI.create("https://api.github.com/repos/zolkers/Anilib/releases/latest");
    private static final String RELEASE_PUBLIC_KEY =
            "MCowBQYDK2VwAyEAPztGsNi+JE6WEb/F+hMurD36N/dzqn6Q5RXtQdv2hvU=";
    private static final PluginManifest MANIFEST = PluginManifest.builder(
                    ComponentDescriptor.of("feature.application-update", "Application updates", "1.0.0"))
            .requires(NetworkCapabilities.HTTP_CLIENT)
            .provides(ApplicationUpdateCapabilities.SERVICE)
            .provides(ApplicationUpdateUiCapabilities.PRESENTATION)
            .build();
    private final ApplicationVersion currentVersion;
    private final ApplicationPlatform platform;
    private final Path channelFile;

    public ApplicationUpdatePlugin(ApplicationVersion currentVersion, ApplicationPlatform platform) {
        this(currentVersion, platform, null);
    }

    public ApplicationUpdatePlugin(
            ApplicationVersion currentVersion,
            ApplicationPlatform platform,
            Path channelFile) {
        this.currentVersion = Objects.requireNonNull(currentVersion, "currentVersion must not be null");
        this.platform = Objects.requireNonNull(platform, "platform must not be null");
        this.channelFile = channelFile == null ? null : channelFile.toAbsolutePath().normalize();
    }

    public static ApplicationUpdatePlugin currentRuntime(Path channelFile) {
        String version = System.getProperty("anilib.version", "1.0.3");
        return new ApplicationUpdatePlugin(
                ApplicationVersion.parse(version),
                ApplicationPlatform.current(),
                channelFile);
    }

    @Override
    public PluginManifest manifest() {
        return MANIFEST;
    }

    @Override
    public void install(PluginInstallationContext context) {
        AnilibHttpClient httpClient = context.require(NetworkCapabilities.HTTP_CLIENT);
        GitHubApplicationUpdateService service = new GitHubApplicationUpdateService(
                httpClient,
                currentVersion,
                platform,
                RELEASE_ENDPOINT,
                RELEASE_PUBLIC_KEY,
                channelFile == null ? null : new FileApplicationUpdateChannelStore(channelFile));
        context.publish(ApplicationUpdateCapabilities.SERVICE, service);
        context.publish(
                ApplicationUpdateUiCapabilities.PRESENTATION,
                new DefaultApplicationUpdatePresentation(service));
    }
}
