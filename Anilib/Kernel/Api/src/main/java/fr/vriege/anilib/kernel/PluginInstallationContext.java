package fr.vriege.anilib.kernel;

/**
 * Installation-scoped access to the validated plugin graph and lifecycle.
 *
 * <p>An instance is supplied to one invocation of
 * {@link AnilibPlugin#install(PluginInstallationContext)}. It enforces the
 * plugin's {@link PluginManifest}: required capabilities may be resolved,
 * provided capabilities may be published, and values may be added to declared
 * contribution points. Undeclared graph access is rejected.</p>
 *
 * <p>The context also owns cleanup on behalf of the plugin. Resources and
 * actions are released in last-in-first-out order during startup rollback,
 * dynamic unregistration, or product shutdown. The context is closed when
 * installation completes or rolls back and must not be retained by the
 * plugin.</p>
 *
 * @see AnilibPlugin#install(PluginInstallationContext)
 * @see PluginManifest
 */
public interface PluginInstallationContext {
    /**
     * Resolves a capability declared as required by the installing plugin.
     *
     * @param key the key of the required capability
     * @param <T> the capability type
     * @return the value published by the capability provider
     * @throws IllegalArgumentException if the plugin did not declare
     *                                  {@code key} as required
     * @throws IllegalStateException if this context is closed
     */
    <T> T require(CapabilityKey<T> key);

    /**
     * Publishes a capability declared as provided by the installing plugin.
     *
     * <p>Each declared capability must be published exactly once before
     * installation returns normally. The runtime type of {@code value} is
     * checked against {@link CapabilityKey#type()}.</p>
     *
     * @param key   the key declared by the plugin manifest
     * @param value the non-null capability value
     * @param <T>   the capability type
     * @throws NullPointerException if {@code value} is {@code null}
     * @throws ClassCastException if {@code value} is not an instance of the
     *                            key's runtime type
     * @throws IllegalArgumentException if the plugin did not declare
     *                                  {@code key} as provided
     * @throws IllegalStateException if this context is closed or the plugin has
     *                               already published a value for {@code key}
     */
    <T> void publish(CapabilityKey<T> key, T value);

    /**
     * Adds a value to a contribution point declared by the installing plugin.
     *
     * <p>The runtime type of {@code value} is checked against
     * {@link ContributionPoint#type()}. More than one value may be contributed
     * to the same point by a single plugin.</p>
     *
     * @param point    the declared contribution point
     * @param priority the ordering priority; larger values are presented first
     * @param value    the non-null contribution value
     * @param <T>      the contribution type
     * @throws NullPointerException if {@code value} is {@code null}
     * @throws ClassCastException if {@code value} is not an instance of the
     *                            point's runtime type
     * @throws IllegalArgumentException if the plugin did not declare
     *                                  {@code point}
     * @throws IllegalStateException if this context is closed
     */
    <T> void contribute(ContributionPoint<T> point, int priority, T value);

    /**
     * Transfers ownership of an {@link AutoCloseable} resource to the plugin
     * lifecycle.
     *
     * <p>Owned resources are closed in reverse registration order. On startup
     * failure, cleanup failures are attached to the resulting
     * {@link PluginStartupException} as suppressed exceptions. The returned
     * value is the same instance, allowing resource creation and ownership to
     * be expressed in one statement.</p>
     *
     * @param resource the non-null resource to own
     * @param <T>      the resource type
     * @return {@code resource}
     * @throws NullPointerException if {@code resource} is {@code null}
     * @throws IllegalStateException if this context is closed
     */
    <T extends AutoCloseable> T own(T resource);

    /**
     * Registers an action to run when this plugin installation is released.
     *
     * <p>Cleanup actions and resources registered through {@link #own} share
     * one last-in-first-out order.</p>
     *
     * @param cleanup the non-null cleanup action
     * @throws NullPointerException if {@code cleanup} is {@code null}
     * @throws IllegalStateException if this context is closed
     */
    void onClose(Runnable cleanup);
}
