package fr.vriege.anilib.kernel;

import fr.vriege.anilib.foundation.component.ComponentDescriptor;

/**
 * A lifecycle handle for a plugin installed into an already started product.
 *
 * <p>Closing the handle unregisters the plugin's contributions and releases
 * the resources it registered during installation. The handle is owned by the
 * {@link StartedAnilib} that created it and should not outlive that product.</p>
 *
 * @see StartedAnilib#install(AnilibPlugin)
 */
public interface PluginRegistration extends AutoCloseable {
    /**
     * Returns the descriptor of the registered plugin.
     *
     * @return the plugin component descriptor
     */
    ComponentDescriptor component();

    /**
     * Unregisters the plugin and releases its lifecycle resources.
     *
     * <p>This method has no effect after this registration has already been
     * closed.</p>
     *
     * @throws PluginStartupException if one or more plugin cleanup actions fail;
     *                                the individual failures are suppressed
     */
    @Override
    void close();
}
