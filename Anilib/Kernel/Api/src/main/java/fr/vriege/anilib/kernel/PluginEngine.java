package fr.vriege.anilib.kernel;

import java.util.Collection;

/**
 * Validates and starts an explicitly selected graph of Anilib plugins.
 *
 * <p>A plugin engine is the entry point to the kernel lifecycle. It derives the
 * product graph exclusively from the supplied plugins and their
 * {@linkplain PluginManifest manifests}; it does not discover plugins by
 * scanning the class path or by consulting a global registry. Capabilities
 * form the dependency edges of that graph: a plugin that
 * {@linkplain PluginManifest#requiredCapabilities() requires} a capability is
 * installed after the plugin that
 * {@linkplain PluginManifest#providedCapabilities() provides} it.</p>
 *
 * <p>Startup is transactional. The complete graph is validated before the
 * first plugin is installed. If installation then fails, lifecycle resources
 * owned through {@link PluginInstallationContext} are released in reverse
 * installation order. Cleanup failures do not replace the startup failure;
 * they are attached to it as suppressed exceptions.</p>
 *
 * <p>A successful startup produces a {@link StartedAnilib} whose product
 * capability graph is immutable. The returned object owns the installed
 * plugins and their lifecycle resources and must therefore be
 * {@linkplain StartedAnilib#close() closed} when the product is no longer in
 * use.</p>
 *
 * @see AnilibPlugin
 * @see PluginManifest
 * @see PluginInstallationContext
 * @see StartedAnilib
 */
public interface PluginEngine {
    /**
     * Validates and starts a product composed of the specified plugins.
     *
     * <p>Before invoking any {@link AnilibPlugin#install(PluginInstallationContext)}
     * method, this method validates that:</p>
     *
     * <ul>
     *   <li>every plugin and plugin manifest is non-null;</li>
     *   <li>every component identifier is unique;</li>
     *   <li>every capability has at most one provider;</li>
     *   <li>every required capability has a provider; and</li>
     *   <li>the resulting capability dependency graph is acyclic.</li>
     * </ul>
     *
     * <p>Plugins are installed only after all providers of their required
     * capabilities. During installation, a plugin may access and publish only
     * the capabilities declared by its manifest, may contribute only to its
     * declared contribution points, and must publish every capability it
     * declared as provided.</p>
     *
     * <p>If a plugin cannot be installed, resources registered by that plugin
     * are released in last-in-first-out order, followed by the resources of
     * previously installed plugins in reverse installation order. No partially
     * started product is returned.</p>
     *
     * @param plugins the explicit plugin selection; may be empty, but must not
     *                be {@code null} or contain {@code null}
     * @return a started product that owns the installed plugins and their
     *         lifecycle resources
     * @throws NullPointerException if {@code plugins}, one of its elements, or
     *                              a plugin manifest is {@code null}
     * @throws PluginStartupException if the plugin graph is invalid, a plugin
     *                                violates its manifest, or installation of
     *                                a plugin fails
     *
     * @apiNote The order of elements in {@code plugins} does not override
     * dependency ordering. Clients should use try-with-resources to ensure that
     * the returned product is closed.
     *
     * @implSpec Implementations must validate the complete graph before
     * installing any plugin and must provide the rollback semantics described
     * above.
     */
    StartedAnilib start(Collection<? extends AnilibPlugin> plugins);
}
