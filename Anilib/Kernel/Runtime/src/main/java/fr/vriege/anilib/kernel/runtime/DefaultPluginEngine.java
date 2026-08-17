package fr.vriege.anilib.kernel.runtime;

import fr.vriege.anilib.foundation.component.ComponentDescriptor;
import fr.vriege.anilib.foundation.component.ComponentId;
import fr.vriege.anilib.kernel.AnilibPlugin;
import fr.vriege.anilib.kernel.CapabilityKey;
import fr.vriege.anilib.kernel.Contribution;
import fr.vriege.anilib.kernel.ContributionPoint;
import fr.vriege.anilib.kernel.PluginEngine;
import fr.vriege.anilib.kernel.PluginInstallationContext;
import fr.vriege.anilib.kernel.PluginManifest;
import fr.vriege.anilib.kernel.PluginStartupException;
import fr.vriege.anilib.kernel.StartedAnilib;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

/** Default deterministic and transactional implementation of the plugin kernel. */
public final class DefaultPluginEngine implements PluginEngine {
    public DefaultPluginEngine() {
    }

    @Override
    public StartedAnilib start(Collection<? extends AnilibPlugin> plugins) {
        GraphPlan plan = GraphPlan.resolve(plugins);
        Map<CapabilityKey<?>, Object> capabilities = new LinkedHashMap<>();
        Map<ContributionPoint<?>, List<Contribution<?>>> contributions = new LinkedHashMap<>();
        List<InstalledPlugin> installed = new ArrayList<>();

        for (PluginNode node : plan.orderedPlugins()) {
            InstallationContext context = new InstallationContext(node.manifest(), capabilities);
            try {
                node.plugin().install(context);
                InstalledPlugin installation = context.complete();
                capabilities.putAll(installation.capabilities());
                installation.contributions().forEach((point, values) ->
                        contributions.computeIfAbsent(point, ignored -> new ArrayList<>()).addAll(values));
                installed.add(installation);
            } catch (Exception | LinkageError error) {
                PluginStartupException failure = new PluginStartupException(
                        "Failed to install plugin " + node.manifest().descriptor().id(), error);
                context.rollbackInto(failure);
                rollback(installed, failure);
                throw failure;
            }
        }

        contributions.values().forEach(values -> values.sort(
                Comparator.<Contribution<?>, Integer>comparing(Contribution::priority).reversed()
                        .thenComparing(Contribution::contributor)));
        return new DefaultStartedAnilib(capabilities, contributions, plan.descriptors(), installed);
    }

    private static void rollback(List<InstalledPlugin> installed, PluginStartupException failure) {
        for (int index = installed.size() - 1; index >= 0; index--) {
            installed.get(index).closeInto(failure);
        }
    }

    private record PluginNode(AnilibPlugin plugin, PluginManifest manifest) {
    }

    private record GraphPlan(List<PluginNode> orderedPlugins, List<ComponentDescriptor> descriptors) {
        private static GraphPlan resolve(Collection<? extends AnilibPlugin> plugins) {
            Objects.requireNonNull(plugins, "plugins must not be null");
            Map<ComponentId, PluginNode> byId = new TreeMap<>();
            Map<CapabilityKey<?>, PluginNode> providers = new TreeMap<>();

            for (AnilibPlugin plugin : plugins) {
                Objects.requireNonNull(plugin, "plugins must not contain null");
                PluginManifest manifest = Objects.requireNonNull(
                        plugin.manifest(), "plugin manifest must not be null");
                ComponentId id = manifest.descriptor().id();
                if (byId.putIfAbsent(id, new PluginNode(plugin, manifest)) != null) {
                    throw new PluginStartupException("Duplicate plugin id: " + id);
                }
                for (CapabilityKey<?> capability : manifest.providedCapabilities()) {
                    PluginNode previous = providers.putIfAbsent(capability, new PluginNode(plugin, manifest));
                    if (previous != null) {
                        throw new PluginStartupException(
                                "Duplicate provider for " + capability + ": "
                                        + previous.manifest().descriptor().id() + " and " + id);
                    }
                }
            }

            List<PluginNode> ordered = new ArrayList<>();
            Map<ComponentId, VisitState> states = new HashMap<>();
            for (PluginNode node : byId.values()) {
                visit(node, providers, states, ordered, new ArrayList<>());
            }
            return new GraphPlan(
                    List.copyOf(ordered),
                    ordered.stream().map(node -> node.manifest().descriptor()).toList());
        }

        private static void visit(
                PluginNode node,
                Map<CapabilityKey<?>, PluginNode> providers,
                Map<ComponentId, VisitState> states,
                List<PluginNode> ordered,
                List<ComponentId> path) {
            ComponentId id = node.manifest().descriptor().id();
            VisitState state = states.get(id);
            if (state == VisitState.VISITED) {
                return;
            }
            if (state == VisitState.VISITING) {
                path.add(id);
                throw new PluginStartupException("Plugin dependency cycle: " + path);
            }

            states.put(id, VisitState.VISITING);
            path.add(id);
            node.manifest().requiredCapabilities().stream().sorted().forEach(required -> {
                PluginNode provider = providers.get(required);
                if (provider == null) {
                    throw new PluginStartupException(id + " requires missing capability " + required);
                }
                visit(provider, providers, states, ordered, new ArrayList<>(path));
            });
            states.put(id, VisitState.VISITED);
            ordered.add(node);
        }
    }

    private enum VisitState {
        VISITING,
        VISITED
    }

    @FunctionalInterface
    private interface Cleanup {
        void close() throws Exception;
    }

    private static final class InstallationContext implements PluginInstallationContext {
        private final PluginManifest manifest;
        private final Map<CapabilityKey<?>, Object> available;
        private final Map<CapabilityKey<?>, Object> published = new LinkedHashMap<>();
        private final Map<ContributionPoint<?>, List<Contribution<?>>> contributions = new LinkedHashMap<>();
        private final List<Cleanup> cleanups = new ArrayList<>();
        private boolean completed;

        private InstallationContext(PluginManifest manifest, Map<CapabilityKey<?>, Object> available) {
            this.manifest = manifest;
            this.available = available;
        }

        @Override
        public <T> T require(CapabilityKey<T> key) {
            ensureOpen();
            if (!manifest.requiredCapabilities().contains(key)) {
                throw new IllegalArgumentException("Plugin did not declare required capability " + key);
            }
            return key.type().cast(Objects.requireNonNull(
                    available.get(key), "Required capability is not installed: " + key));
        }

        @Override
        public <T> void publish(CapabilityKey<T> key, T value) {
            ensureOpen();
            if (!manifest.providedCapabilities().contains(key)) {
                throw new IllegalArgumentException("Plugin did not declare provided capability " + key);
            }
            Object typedValue = key.type().cast(Objects.requireNonNull(value, "value must not be null"));
            if (published.putIfAbsent(key, typedValue) != null) {
                throw new IllegalStateException("Capability already published by plugin: " + key);
            }
        }

        @Override
        public <T> void contribute(ContributionPoint<T> point, int priority, T value) {
            ensureOpen();
            if (!manifest.contributionPoints().contains(point)) {
                throw new IllegalArgumentException("Plugin did not declare contribution point " + point);
            }
            T typedValue = point.type().cast(Objects.requireNonNull(value, "value must not be null"));
            Contribution<T> contribution = new Contribution<>(manifest.descriptor().id(), priority, typedValue);
            contributions.computeIfAbsent(point, ignored -> new ArrayList<>()).add(contribution);
        }

        @Override
        public <T extends AutoCloseable> T own(T resource) {
            ensureOpen();
            Objects.requireNonNull(resource, "resource must not be null");
            cleanups.add(resource::close);
            return resource;
        }

        @Override
        public void onClose(Runnable cleanup) {
            ensureOpen();
            Objects.requireNonNull(cleanup, "cleanup must not be null");
            cleanups.add(cleanup::run);
        }

        private InstalledPlugin complete() {
            ensureOpen();
            Set<CapabilityKey<?>> missing = new java.util.TreeSet<>(manifest.providedCapabilities());
            missing.removeAll(published.keySet());
            if (!missing.isEmpty()) {
                throw new IllegalStateException("Plugin did not publish declared capabilities: " + missing);
            }
            completed = true;
            return new InstalledPlugin(
                    manifest.descriptor(),
                    Map.copyOf(published),
                    immutableContributions(contributions),
                    List.copyOf(cleanups));
        }

        private void rollbackInto(PluginStartupException failure) {
            if (!completed) {
                closeReverse(cleanups, failure);
                completed = true;
            }
        }

        private void ensureOpen() {
            if (completed) {
                throw new IllegalStateException("Plugin installation context is closed");
            }
        }
    }

    private record InstalledPlugin(
            ComponentDescriptor descriptor,
            Map<CapabilityKey<?>, Object> capabilities,
            Map<ContributionPoint<?>, List<Contribution<?>>> contributions,
            List<Cleanup> cleanups) {

        private void closeInto(PluginStartupException failure) {
            closeReverse(cleanups, failure);
        }
    }

    private static final class DefaultStartedAnilib implements StartedAnilib {
        private final Map<CapabilityKey<?>, Object> capabilities;
        private final Map<ContributionPoint<?>, List<Contribution<?>>> contributions;
        private final List<ComponentDescriptor> components;
        private final List<InstalledPlugin> installed;
        private boolean closed;

        private DefaultStartedAnilib(
                Map<CapabilityKey<?>, Object> capabilities,
                Map<ContributionPoint<?>, List<Contribution<?>>> contributions,
                List<ComponentDescriptor> components,
                List<InstalledPlugin> installed) {
            this.capabilities = Map.copyOf(capabilities);
            this.contributions = immutableContributions(contributions);
            this.components = List.copyOf(components);
            this.installed = List.copyOf(installed);
        }

        @Override
        public synchronized <T> T capability(CapabilityKey<T> key) {
            ensureOpen();
            return key.type().cast(Objects.requireNonNull(
                    capabilities.get(key), "Capability is not installed: " + key));
        }

        @Override
        public synchronized <T> List<Contribution<T>> contributions(ContributionPoint<T> point) {
            ensureOpen();
            List<Contribution<?>> values = contributions.getOrDefault(point, List.of());
            return values.stream()
                    .map(value -> new Contribution<>(
                            value.contributor(), value.priority(), point.type().cast(value.value())))
                    .toList();
        }

        @Override
        public synchronized List<ComponentDescriptor> components() {
            ensureOpen();
            return components;
        }

        @Override
        public synchronized void close() {
            if (closed) {
                return;
            }
            closed = true;
            PluginStartupException failure = new PluginStartupException("One or more plugins failed to close");
            for (int index = installed.size() - 1; index >= 0; index--) {
                installed.get(index).closeInto(failure);
            }
            if (failure.getSuppressed().length > 0) {
                throw failure;
            }
        }

        private void ensureOpen() {
            if (closed) {
                throw new IllegalStateException("Anilib product is closed");
            }
        }
    }

    private static Map<ContributionPoint<?>, List<Contribution<?>>> immutableContributions(
            Map<ContributionPoint<?>, List<Contribution<?>>> source) {
        Map<ContributionPoint<?>, List<Contribution<?>>> copy = new LinkedHashMap<>();
        source.forEach((point, values) -> copy.put(point, List.copyOf(values)));
        return Map.copyOf(copy);
    }

    private static void closeReverse(List<Cleanup> cleanups, PluginStartupException failure) {
        for (int index = cleanups.size() - 1; index >= 0; index--) {
            try {
                cleanups.get(index).close();
            } catch (Exception | LinkageError cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
            }
        }
    }
}
