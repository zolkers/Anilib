package fr.vriege.anilib.kernel;

/**
 * A selectable unit of composition in an Anilib product.
 *
 * <p>Each plugin declares its identity, dependencies, publications, and
 * contribution points in a side-effect-free {@link PluginManifest}. The
 * {@link PluginEngine} validates those declarations as a complete graph before
 * it invokes {@link #install(PluginInstallationContext)}.</p>
 *
 * <p>Implementations should keep construction and manifest access free of
 * externally visible side effects. Resources needed by the running product
 * should be created during installation and registered with the supplied
 * context so that startup rollback and normal shutdown can release them.</p>
 *
 * @see PluginManifest
 * @see PluginInstallationContext
 * @see PluginEngine
 */
public interface AnilibPlugin {
    /**
     * Returns the complete declaration of this plugin.
     *
     * <p>The returned manifest is used for graph validation and must describe
     * every capability accessed or published and every contribution point used
     * by {@link #install(PluginInstallationContext)}.</p>
     *
     * @return the non-null, immutable manifest of this plugin
     *
     * @implSpec This method must be side-effect-free and must return a stable
     * manifest for the lifetime of this plugin instance.
     */
    PluginManifest manifest();

    /**
     * Installs this plugin into a validated product graph.
     *
     * <p>When this method is invoked, all capabilities declared as required by
     * the plugin are already installed. The implementation must publish every
     * declared provided capability before returning normally. It may also add
     * declared contributions and transfer ownership of resources or cleanup
     * actions to {@code context}.</p>
     *
     * <p>If this method throws, the engine rolls back resources already owned
     * by this installation and then rolls back previously installed plugins.
     * The context must not be retained or used after this method returns.</p>
     *
     * @param context the installation-scoped access, publication, contribution,
     *                and lifecycle context
     * @throws Exception if this plugin cannot be installed
     *
     * @see PluginInstallationContext#require(CapabilityKey)
     * @see PluginInstallationContext#publish(CapabilityKey, Object)
     * @see PluginInstallationContext#own(AutoCloseable)
     */
    void install(PluginInstallationContext context) throws Exception;
}
