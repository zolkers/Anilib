package fr.vriege.anilib.tooling.archtests;

import fr.vriege.anilib.feature.source.Source;
import fr.vriege.anilib.feature.source.SourceApiVersion;
import fr.vriege.anilib.feature.source.SourceCapabilities;
import fr.vriege.anilib.feature.source.SourceContentKind;
import fr.vriege.anilib.feature.source.SourceDescriptor;
import fr.vriege.anilib.feature.source.SourceExtensionManifest;
import fr.vriege.anilib.feature.source.SourceExtensionPlugin;
import fr.vriege.anilib.feature.source.SourceId;
import fr.vriege.anilib.feature.source.SourceNetworkOrigin;
import fr.vriege.anilib.feature.source.SourcePermission;
import fr.vriege.anilib.feature.source.SourcePermissionException;
import fr.vriege.anilib.feature.source.SourceRegistry;
import fr.vriege.anilib.feature.source.SourceSdk;
import fr.vriege.anilib.feature.source.SourceWebPage;
import fr.vriege.anilib.feature.source.bundle.SourceSdkPlugin;
import fr.vriege.anilib.feature.network.NetworkCapabilities;
import fr.vriege.anilib.framework.http.AnilibHttpClient;
import fr.vriege.anilib.framework.http.HttpRequest;
import fr.vriege.anilib.framework.http.HttpResponse;
import fr.vriege.anilib.foundation.component.ComponentDescriptor;
import fr.vriege.anilib.kernel.AnilibPlugin;
import fr.vriege.anilib.kernel.PluginManifest;
import fr.vriege.anilib.kernel.PluginStartupException;
import fr.vriege.anilib.kernel.StartedAnilib;
import fr.vriege.anilib.kernel.runtime.DefaultPluginEngine;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

final class SourceExtensionSdkTest {
    private SourceExtensionSdkTest() {
    }

    static int run() {
        Counter counter = new Counter();
        verifiesRegistry(counter);
        rejectsDuplicateSourceIds(counter);
        rejectsIncompatibleApi(counter);
        validatesDescriptors(counter);
        validatesBrowserPages(counter);
        enforcesExplicitPermissions(counter);
        rejectsInvalidExtensionDeclarations(counter);
        return counter.value;
    }

    private static void verifiesRegistry(Counter counter) {
        Source first = source("test.alpha", "Alpha", SourceSdk.API_VERSION);
        Source second = source("test.zeta", "Zeta", SourceSdk.API_VERSION);
        SourceRegistry registry;
        try (StartedAnilib application = new DefaultPluginEngine().start(List.of(
                extension("extension.zeta", second),
                new SourceSdkPlugin(),
                extension("extension.alpha", first)))) {
            registry = application.capability(SourceCapabilities.REGISTRY);
            counter.check(
                    registry.sources().stream().map(source -> source.descriptor().id()).toList()
                            .equals(List.of(SourceId.of("test.alpha"), SourceId.of("test.zeta"))),
                    "source registry must expose deterministic id ordering");
            counter.check(registry.find(SourceId.of("test.alpha")).orElseThrow() == first,
                    "source registry must resolve the installed source instance");
            counter.check(registry.find(SourceId.of("test.missing")).isEmpty(),
                    "source registry must report an unknown source explicitly");
            counter.check(registry.extensions().size() == 2,
                    "explicit source Bundles must publish observable extension metadata");
        }
        counter.expectIllegalState(registry::sources,
                "source registry must close with its product");
    }

    private static void rejectsDuplicateSourceIds(Counter counter) {
        Source first = source("test.duplicate", "First", SourceSdk.API_VERSION);
        Source second = source("test.duplicate", "Second", SourceSdk.API_VERSION);
        counter.expectStartupFailure(() -> new DefaultPluginEngine().start(List.of(
                new SourceSdkPlugin(),
                extension("extension.duplicate-first", first),
                extension("extension.duplicate-second", second))),
                "duplicate source ids must fail product startup");
    }

    private static void rejectsIncompatibleApi(Counter counter) {
        Source future = source("test.future", "Future", new SourceApiVersion(2, 0));
        counter.expectStartupFailure(() -> new DefaultPluginEngine().start(List.of(
                new SourceSdkPlugin(),
                extension("extension.future", future))),
                "a source requiring another major API must fail product startup");
        counter.check(SourceSdk.API_VERSION.supports(new SourceApiVersion(1, 0)),
                "current Source API must support its own baseline");
        counter.check(SourceSdk.API_VERSION.supports(new SourceApiVersion(1, 1)),
                "current Source API must support its catalogue contract");
        counter.check(SourceSdk.API_VERSION.supports(new SourceApiVersion(1, 2)),
                "current Source API must support its permission contract");
        counter.check(SourceSdk.API_VERSION.supports(new SourceApiVersion(1, 3)),
                "current Source API must support its paged-content contract");
        counter.check(SourceSdk.API_VERSION.supports(new SourceApiVersion(1, 4)),
                "current Source API must support its streaming-content contract");
        counter.check(SourceSdk.API_VERSION.supports(new SourceApiVersion(1, 5)),
                "current Source API must support its browser entry-point contract");
        counter.check(SourceSdk.API_VERSION.supports(new SourceApiVersion(1, 6)),
                "current Source API must support its browser-policy contract");
        counter.check(SourceSdk.API_VERSION.supports(new SourceApiVersion(1, 7)),
                "current Source API must support refresh and episode thumbnails");
        counter.check(!SourceSdk.API_VERSION.supports(new SourceApiVersion(1, 8)),
                "current Source API must reject a newer minor contract");
    }

    private static void validatesBrowserPages(Counter counter) {
        SourceWebPage page = new SourceWebPage(
                URI.create("https://catalogue.example.test/challenge"),
                Map.of("Referer", "https://catalogue.example.test/"),
                Optional.of("Test Browser/1.0"),
                Set.of("cf_clearance"));
        counter.check(page.location().getHost().equals("catalogue.example.test")
                        && page.completionCookies().equals(Set.of("cf_clearance")),
                "browser pages must retain immutable source request and challenge policy");
        counter.expectIllegalArgument(() -> SourceWebPage.of(URI.create("file:///tmp/source.html")),
                "browser pages must reject non-web schemes");
        counter.expectIllegalArgument(() -> new SourceWebPage(
                        URI.create("https://catalogue.example.test/"),
                        Map.of("Cookie", "session=secret"),
                        Optional.empty(),
                        Set.of()),
                "browser pages must keep cookies under the shared cookie jar");
        counter.expectIllegalArgument(() -> new SourceWebPage(
                        URI.create("https://catalogue.example.test/"),
                        Map.of(),
                        Optional.empty(),
                        Set.of("invalid cookie")),
                "challenge completion cookies must use valid names");
    }

    private static void validatesDescriptors(Counter counter) {
        SourceDescriptor descriptor = descriptor("test.language", "Language", SourceSdk.API_VERSION, "en-us");
        counter.check(descriptor.languageTag().equals("en-US"),
                "source language tags must be normalized");
        counter.expectIllegalArgument(() -> descriptor("test.invalid", "Invalid", SourceSdk.API_VERSION, "???"),
                "invalid source language tags must be rejected");
        counter.expectIllegalArgument(() -> new SourceDescriptor(
                SourceId.of("test.empty-kinds"),
                "Empty kinds",
                "1.0.0",
                "und",
                Set.of(),
                SourceSdk.API_VERSION),
                "source descriptors must declare at least one content kind");
    }

    private static void enforcesExplicitPermissions(Counter counter) {
        SourceId sourceId = SourceId.of("test.networked");
        SourceNetworkOrigin allowedOrigin = SourceNetworkOrigin.https("api.example.test");
        SourceExtensionManifest extensionManifest = SourceExtensionManifest.networked(
                ComponentDescriptor.of("extension.networked", "Networked", "1.0.0"),
                sourceId,
                Set.of(allowedOrigin));
        AtomicInteger exchanges = new AtomicInteger();
        AtomicReference<TestNetworkSource> created = new AtomicReference<>();
        SourceExtensionPlugin extension = new SourceExtensionPlugin(extensionManifest, context -> {
            counter.check(context.grantedPermissions().equals(Set.of(SourcePermission.NETWORK)),
                    "source factory must observe only its declared permission grants");
            TestNetworkSource source = new TestNetworkSource(
                    descriptor(sourceId.toString(), "Networked", SourceSdk.API_VERSION, "en"),
                    context.httpClient());
            created.set(source);
            return source;
        });

        try (StartedAnilib application = new DefaultPluginEngine().start(List.of(
                new SourceSdkPlugin(),
                networkProvider(exchanges),
                extension))) {
            TestNetworkSource source = created.get();
            counter.check(source.fetch("https://api.example.test/catalog").statusCode() == 204,
                    "an allowed exact origin must reach the product HTTP client");
            counter.check(exchanges.get() == 1,
                    "the restricted source client must delegate one allowed exchange");
            counter.expectSourcePermission(
                    () -> source.fetch("https://other.example.test/catalog"),
                    "a source must not reach an undeclared host");
            counter.expectSourcePermission(
                    () -> source.fetch("https://api.example.test:8443/catalog"),
                    "a source grant must include the exact port");
            counter.check(application.capability(SourceCapabilities.REGISTRY)
                            .extensions().getFirst().manifest().equals(extensionManifest),
                    "the registry must retain the permission manifest for shared UIs");
        }

        SourceExtensionManifest offlineManifest = SourceExtensionManifest.offline(
                ComponentDescriptor.of("extension.offline", "Offline", "1.0.0"),
                SourceId.of("test.offline"));
        SourceExtensionPlugin denied = new SourceExtensionPlugin(offlineManifest, context -> {
            context.httpClient();
            return source("test.offline", "Offline", SourceSdk.API_VERSION);
        });
        counter.expectStartupFailure(() -> new DefaultPluginEngine().start(List.of(
                new SourceSdkPlugin(),
                denied)),
                "an offline source factory must not obtain the HTTP capability");
        counter.expectStartupFailure(() -> new DefaultPluginEngine().start(List.of(
                new SourceSdkPlugin(),
                extension)),
                "a networked source Bundle must declare the product network capability");
    }

    private static void rejectsInvalidExtensionDeclarations(Counter counter) {
        ComponentDescriptor component = ComponentDescriptor.of(
                "extension.invalid-permissions",
                "Invalid permissions",
                "1.0.0");
        counter.expectIllegalArgument(() -> new SourceExtensionManifest(
                        component,
                        SourceId.of("test.no-origin"),
                        Set.of(SourcePermission.NETWORK),
                        Set.of()),
                "NETWORK permission must declare at least one exact origin");
        counter.expectIllegalArgument(() -> new SourceExtensionManifest(
                        component,
                        SourceId.of("test.cleartext"),
                        Set.of(SourcePermission.NETWORK),
                        Set.of(SourceNetworkOrigin.http("example.test"))),
                "HTTP origins must declare the cleartext permission visibly");
        SourceExtensionPlugin mismatched = new SourceExtensionPlugin(
                SourceExtensionManifest.offline(component, SourceId.of("test.expected")),
                context -> source("test.actual", "Actual", SourceSdk.API_VERSION));
        counter.expectStartupFailure(() -> new DefaultPluginEngine().start(List.of(
                new SourceSdkPlugin(),
                mismatched)),
                "a source factory must create exactly the identity declared by its Bundle");
    }

    private static AnilibPlugin networkProvider(AtomicInteger exchanges) {
        AnilibHttpClient client = request -> {
            exchanges.incrementAndGet();
            return new HttpResponse(204, Map.of(), new byte[0], false);
        };
        return new AnilibPlugin() {
            private final PluginManifest manifest = PluginManifest.builder(
                            ComponentDescriptor.of("test.network", "Test network", "1.0.0"))
                    .provides(NetworkCapabilities.HTTP_CLIENT)
                    .build();

            @Override
            public PluginManifest manifest() {
                return manifest;
            }

            @Override
            public void install(fr.vriege.anilib.kernel.PluginInstallationContext context) {
                context.publish(NetworkCapabilities.HTTP_CLIENT, client);
            }
        };
    }

    private static SourceExtensionPlugin extension(String componentId, Source source) {
        return new SourceExtensionPlugin(
                ComponentDescriptor.of(componentId, componentId, "1.0.0"),
                source);
    }

    private static Source source(String id, String name, SourceApiVersion apiVersion) {
        return new TestSource(descriptor(id, name, apiVersion, "und"));
    }

    private static SourceDescriptor descriptor(
            String id,
            String name,
            SourceApiVersion apiVersion,
            String languageTag) {
        return new SourceDescriptor(
                SourceId.of(id),
                name,
                "1.0.0",
                languageTag,
                Set.of(SourceContentKind.MANGA),
                apiVersion);
    }

    private record TestSource(SourceDescriptor descriptor) implements Source {
    }

    private record TestNetworkSource(
            SourceDescriptor descriptor,
            AnilibHttpClient client) implements Source {
        private HttpResponse fetch(String uri) {
            return client.execute(HttpRequest.builder(URI.create(uri)).build());
        }
    }

    private static final class Counter {
        private int value;

        private void check(boolean condition, String message) {
            value++;
            if (!condition) {
                throw new AssertionError(message);
            }
        }

        private void expectStartupFailure(Runnable action, String message) {
            try {
                action.run();
                throw new AssertionError(message);
            } catch (PluginStartupException expected) {
                value++;
            }
        }

        private void expectIllegalArgument(Runnable action, String message) {
            try {
                action.run();
                throw new AssertionError(message);
            } catch (IllegalArgumentException expected) {
                value++;
            }
        }

        private void expectIllegalState(Runnable action, String message) {
            try {
                action.run();
                throw new AssertionError(message);
            } catch (IllegalStateException expected) {
                value++;
            }
        }

        private void expectSourcePermission(Runnable action, String message) {
            try {
                action.run();
                throw new AssertionError(message);
            } catch (SourcePermissionException expected) {
                value++;
            }
        }
    }
}
