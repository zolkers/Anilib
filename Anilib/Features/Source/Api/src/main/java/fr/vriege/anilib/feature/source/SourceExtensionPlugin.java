package fr.vriege.anilib.feature.source;

import fr.vriege.anilib.foundation.component.ComponentDescriptor;
import fr.vriege.anilib.foundation.validation.Preconditions;
import fr.vriege.anilib.feature.network.NetworkCapabilities;
import fr.vriege.anilib.framework.http.AnilibHttpClient;
import fr.vriege.anilib.framework.http.HttpRequest;
import fr.vriege.anilib.framework.http.HttpResponse;
import fr.vriege.anilib.kernel.AnilibPlugin;
import fr.vriege.anilib.kernel.PluginInstallationContext;
import fr.vriege.anilib.kernel.PluginManifest;

import java.util.Objects;
import java.util.Set;

public final class SourceExtensionPlugin implements AnilibPlugin {
    private final PluginManifest manifest;
    private final SourceExtensionManifest extensionManifest;
    private final SourceExtensionFactory factory;

    public SourceExtensionPlugin(ComponentDescriptor component, Source source) {
        this(legacyManifest(component, source), ignored -> source);
    }

    public SourceExtensionPlugin(
            SourceExtensionManifest extensionManifest,
            SourceExtensionFactory factory) {
        this.extensionManifest = Preconditions.requireNonNull(extensionManifest, "extensionManifest");
        this.factory = Preconditions.requireNonNull(factory, "factory");
        PluginManifest.Builder builder = PluginManifest.builder(extensionManifest.component())
                .requires(SourceCapabilities.REGISTRAR);
        if (extensionManifest.permissions().contains(SourcePermission.NETWORK)) {
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
        SourceRegistrar registrar = context.require(SourceCapabilities.REGISTRAR);
        AnilibHttpClient client = extensionManifest.permissions().contains(SourcePermission.NETWORK)
                ? context.require(NetworkCapabilities.HTTP_CLIENT)
                : null;
        SourceExtensionContext extensionContext = new RestrictedContext(extensionManifest, client);
        Source source = Objects.requireNonNull(
                factory.create(extensionContext),
                "source factory must not return null");
        SourceDescriptor descriptor = Objects.requireNonNull(
                source.descriptor(),
                "source descriptor must not be null");
        if (!descriptor.id().equals(extensionManifest.sourceId())) {
            throw new SourceRegistrationException(
                    "Extension " + extensionManifest.component().id() + " declares source "
                            + extensionManifest.sourceId() + " but created " + descriptor.id());
        }
        context.own(registrar.register(extensionManifest, source));
    }

    private static SourceExtensionManifest legacyManifest(
            ComponentDescriptor component,
            Source source) {
        Source value = Preconditions.requireNonNull(source, "source");
        SourceDescriptor descriptor = Objects.requireNonNull(
                value.descriptor(),
                "source descriptor must not be null");
        return SourceExtensionManifest.offline(
                Preconditions.requireNonNull(component, "component"),
                descriptor.id());
    }

    private static final class RestrictedContext implements SourceExtensionContext {
        private final SourceExtensionManifest manifest;
        private final AnilibHttpClient client;

        private RestrictedContext(SourceExtensionManifest manifest, AnilibHttpClient client) {
            this.manifest = manifest;
            this.client = client;
        }

        @Override
        public Set<SourcePermission> grantedPermissions() {
            return manifest.permissions();
        }

        @Override
        public AnilibHttpClient httpClient() {
            if (client == null) {
                throw new SourcePermissionException(
                        "Source " + manifest.sourceId() + " was not granted NETWORK");
            }
            return new RestrictedHttpClient(manifest, client);
        }
    }

    private record RestrictedHttpClient(
            SourceExtensionManifest manifest,
            AnilibHttpClient delegate) implements AnilibHttpClient {
        @Override
        public HttpResponse execute(HttpRequest request) {
            HttpRequest value = Objects.requireNonNull(request, "request must not be null");
            boolean allowed = manifest.networkOrigins().stream()
                    .anyMatch(origin -> origin.matches(value.uri()));
            if (!allowed) {
                throw new SourcePermissionException(
                        "Source " + manifest.sourceId() + " cannot access origin "
                                + requestOrigin(value));
            }
            return delegate.execute(value);
        }

        private static String requestOrigin(HttpRequest request) {
            int port = request.uri().getPort();
            return request.uri().getScheme() + "://" + request.uri().getHost()
                    + (port < 0 ? "" : ":" + port);
        }
    }
}
