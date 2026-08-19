package fr.vriege.anilib.kernel;

import fr.vriege.anilib.foundation.component.ComponentDescriptor;

import java.util.List;

/**
 * A successfully started Anilib product and the owner of its plugin lifecycle.
 *
 * <p>The bootstrap capability graph is immutable: capabilities published at
 * startup remain available until this product is closed and cannot be replaced
 * by later plugin installation. Explicit leaf plugins may be installed at run
 * time through {@link #install(AnilibPlugin)}; they may consume existing
 * capabilities and add contributions, but may not publish capabilities.</p>
 *
 * <p>All operations other than repeated calls to {@link #close()} require an
 * open product. Closing releases installed plugins in reverse installation
 * order. Applications should use this interface with try-with-resources.</p>
 *
 * @see PluginEngine#start(java.util.Collection)
 * @see PluginRegistration
 */
public interface StartedAnilib extends AutoCloseable {
    /**
     * Returns a capability from the immutable product graph.
     *
     * @param key the key of the capability to retrieve
     * @param <T> the capability type
     * @return the installed capability value
     * @throws NullPointerException if {@code key} is {@code null} or the
     *                              capability is not installed
     * @throws IllegalStateException if this product is closed
     */
    <T> T capability(CapabilityKey<T> key);

    /**
     * Returns the current contributions to a typed point.
     *
     * <p>The returned list is an immutable snapshot ordered by descending
     * priority and then by contributor identity. It is empty if no plugin has
     * contributed to {@code point}. Later dynamic installation or
     * unregistration does not alter an earlier snapshot.</p>
     *
     * @param point the contribution point to query
     * @param <T>   the contribution value type
     * @return an immutable, deterministically ordered contribution snapshot
     * @throws IllegalStateException if this product is closed
     */
    <T> List<Contribution<T>> contributions(ContributionPoint<T> point);

    /**
     * Returns the descriptors of the currently installed plugins.
     *
     * <p>The returned list is an immutable snapshot in installation order.
     * Bootstrap plugins follow capability dependency order; dynamically
     * installed plugins are appended while registered.</p>
     *
     * @return the current component inventory
     * @throws IllegalStateException if this product is closed
     */
    List<ComponentDescriptor> components();

    /**
     * Transactionally installs an explicit leaf plugin into this running
     * product.
     *
     * <p>The plugin may require capabilities already present in the immutable
     * product graph and may add declared contributions. It must not declare any
     * provided capability. Its component identifier must be distinct from all
     * currently installed plugins. If installation fails, its owned resources
     * are rolled back and no registration is returned.</p>
     *
     * @param plugin the non-null plugin to install
     * @return a handle that owns this dynamic registration
     * @throws NullPointerException if {@code plugin} or its manifest is
     *                              {@code null}
     * @throws PluginStartupException if the plugin identifier is already in
     *                                use, a required capability is unavailable,
     *                                the plugin declares a provided capability,
     *                                violates its manifest, or fails to install
     * @throws IllegalStateException if this product is closed
     */
    PluginRegistration install(AnilibPlugin plugin);

    /**
     * Closes the product and releases all installed plugins in reverse
     * installation order.
     *
     * <p>This method has no effect after the product has already been closed.
     * If cleanup fails, all remaining cleanup actions are still attempted.</p>
     *
     * @throws PluginStartupException if one or more cleanup actions fail; the
     *                                individual failures are suppressed
     */
    @Override
    void close();
}
