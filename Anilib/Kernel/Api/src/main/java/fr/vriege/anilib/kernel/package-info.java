/**
 * Defines the typed plugin graph and lifecycle contracts of the Anilib kernel.
 *
 * <p>An Anilib product is assembled from an explicit collection of
 * {@link fr.vriege.anilib.kernel.AnilibPlugin} instances. Each plugin exposes a
 * side-effect-free {@link fr.vriege.anilib.kernel.PluginManifest} containing a
 * component descriptor and three kinds of declaration:</p>
 *
 * <ul>
 *   <li>{@linkplain fr.vriege.anilib.kernel.CapabilityKey required
 *       capabilities}, which form dependency edges;</li>
 *   <li>provided capabilities, each of which has exactly one provider in a
 *       started product; and</li>
 *   <li>{@linkplain fr.vriege.anilib.kernel.ContributionPoint contribution
 *       points}, which accept ordered values from multiple plugins.</li>
 * </ul>
 *
 * <p>The {@link fr.vriege.anilib.kernel.PluginEngine} validates the complete
 * graph before installation. It rejects duplicate components or providers,
 * missing dependencies, dependency cycles, and installation behavior that
 * does not match a manifest. Installation and cleanup are transactional:
 * resources owned by a
 * {@link fr.vriege.anilib.kernel.PluginInstallationContext} are released in
 * reverse order if startup fails.</p>
 *
 * <p>A minimal capability provider can be declared as follows:</p>
 *
 * <pre>{@code
 * CapabilityKey<Clock> clock = CapabilityKey.of("example.clock", Clock.class);
 *
 * AnilibPlugin plugin = new AnilibPlugin() {
 *     private final PluginManifest manifest = PluginManifest.builder(
 *             ComponentDescriptor.of("example.clock-plugin", "Clock", "1.0"))
 *             .provides(clock)
 *             .build();
 *
 *     @Override
 *     public PluginManifest manifest() {
 *         return manifest;
 *     }
 *
 *     @Override
 *     public void install(PluginInstallationContext context) {
 *         context.publish(clock, Clock.systemUTC());
 *     }
 * };
 * }</pre>
 *
 * <p>The package deliberately defines no class-path scanning, reflection-based
 * injection, or global service locator. Product configurations select plugin
 * instances explicitly, and callers own the returned
 * {@link fr.vriege.anilib.kernel.StartedAnilib} lifecycle.</p>
 */
package fr.vriege.anilib.kernel;

import fr.vriege.anilib.foundation.component.ComponentDescriptor;

import java.time.Clock;
