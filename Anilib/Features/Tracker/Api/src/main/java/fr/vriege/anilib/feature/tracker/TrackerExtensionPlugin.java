package fr.vriege.anilib.feature.tracker;

import fr.vriege.anilib.feature.network.NetworkCapabilities;
import fr.vriege.anilib.framework.http.AnilibHttpClient;
import fr.vriege.anilib.framework.http.HttpRequest;
import fr.vriege.anilib.framework.http.HttpResponse;
import fr.vriege.anilib.kernel.AnilibPlugin;
import fr.vriege.anilib.kernel.PluginInstallationContext;
import fr.vriege.anilib.kernel.PluginManifest;

import java.util.Objects;
import java.util.Set;

public final class TrackerExtensionPlugin implements AnilibPlugin {
    private final PluginManifest manifest;
    private final TrackerExtensionManifest extensionManifest;
    private final TrackerExtensionFactory factory;

    public TrackerExtensionPlugin(
            TrackerExtensionManifest extensionManifest,
            TrackerExtensionFactory factory) {
        this.extensionManifest = Objects.requireNonNull(
                extensionManifest,
                "extensionManifest must not be null");
        this.factory = Objects.requireNonNull(factory, "factory must not be null");
        PluginManifest.Builder builder = PluginManifest.builder(extensionManifest.component())
                .requires(TrackerCapabilities.REGISTRAR);
        if (extensionManifest.permissions().contains(TrackerPermission.NETWORK)) {
            builder.requires(NetworkCapabilities.HTTP_CLIENT);
        }
        manifest = builder.build();
    }

    @Override
    public PluginManifest manifest() {
        return manifest;
    }

    @Override
    public void install(PluginInstallationContext context) {
        TrackerRegistrar registrar = context.require(TrackerCapabilities.REGISTRAR);
        AnilibHttpClient client = extensionManifest.permissions().contains(TrackerPermission.NETWORK)
                ? context.require(NetworkCapabilities.HTTP_CLIENT)
                : null;
        TrackerExtensionContext extensionContext = new RestrictedContext(extensionManifest, client);
        Tracker tracker = Objects.requireNonNull(
                factory.create(extensionContext),
                "tracker factory must not return null");
        if (!tracker.descriptor().id().equals(extensionManifest.trackerId())) {
            throw new TrackerException("Tracker manifest identity does not match its descriptor");
        }
        context.own(registrar.register(extensionManifest, tracker));
    }

    private record RestrictedContext(
            TrackerExtensionManifest manifest,
            AnilibHttpClient client) implements TrackerExtensionContext {
        @Override
        public Set<TrackerPermission> grantedPermissions() {
            return manifest.permissions();
        }

        @Override
        public AnilibHttpClient httpClient() {
            if (client == null) {
                throw new TrackerException("Tracker " + manifest.trackerId() + " was not granted NETWORK");
            }
            return request -> executeRestricted(client, manifest, request);
        }

        private static HttpResponse executeRestricted(
                AnilibHttpClient client,
                TrackerExtensionManifest manifest,
                HttpRequest request) {
            HttpRequest value = Objects.requireNonNull(request, "request must not be null");
            boolean allowed = manifest.networkOrigins().stream()
                    .anyMatch(origin -> origin.matches(value.uri()));
            if (!allowed) {
                throw new TrackerException(
                        "Tracker " + manifest.trackerId() + " cannot access " + value.uri());
            }
            return client.execute(value);
        }
    }
}
