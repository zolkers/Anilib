package fr.vriege.anilib.tooling.archtests;

import fr.vriege.anilib.feature.source.Source;
import fr.vriege.anilib.feature.source.SourceApiVersion;
import fr.vriege.anilib.feature.source.SourceCapabilities;
import fr.vriege.anilib.feature.source.SourceContentKind;
import fr.vriege.anilib.feature.source.SourceDescriptor;
import fr.vriege.anilib.feature.source.SourceExtensionPlugin;
import fr.vriege.anilib.feature.source.SourceId;
import fr.vriege.anilib.feature.source.SourceRegistry;
import fr.vriege.anilib.feature.source.SourceSdk;
import fr.vriege.anilib.feature.source.bundle.SourceSdkPlugin;
import fr.vriege.anilib.foundation.component.ComponentDescriptor;
import fr.vriege.anilib.kernel.PluginStartupException;
import fr.vriege.anilib.kernel.StartedAnilib;
import fr.vriege.anilib.kernel.runtime.DefaultPluginEngine;

import java.util.List;
import java.util.Set;

/** Contract checks for explicit, versioned, deterministic source extensions. */
final class SourceExtensionSdkTest {
    private SourceExtensionSdkTest() {
    }

    static int run() {
        Counter counter = new Counter();
        verifiesRegistry(counter);
        rejectsDuplicateSourceIds(counter);
        rejectsIncompatibleApi(counter);
        validatesDescriptors(counter);
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
        counter.check(!SourceSdk.API_VERSION.supports(new SourceApiVersion(1, 2)),
                "current Source API must reject a newer minor contract");
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
    }
}
