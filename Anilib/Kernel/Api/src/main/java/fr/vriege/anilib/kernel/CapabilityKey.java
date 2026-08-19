package fr.vriege.anilib.kernel;

import fr.vriege.anilib.foundation.component.ComponentId;
import fr.vriege.anilib.foundation.validation.Preconditions;

/**
 * A stable, type-safe key for one service published into the product graph.
 *
 * <p>A key combines a globally stable component-style identifier with the Java
 * type of the capability value. Both components participate in equality, hash
 * code, and natural ordering, so two keys with the same identifier but
 * different types remain distinct. Plugins use keys in their manifests and
 * installation code; clients use the same keys to retrieve services from a
 * {@link StartedAnilib}.</p>
 *
 * @param <T>  the exposed capability type
 * @param id   the stable identity of the capability
 * @param type the runtime type used to validate published and retrieved values
 *
 * @see PluginManifest#requiredCapabilities()
 * @see PluginManifest#providedCapabilities()
 * @see PluginInstallationContext#publish(CapabilityKey, Object)
 * @see StartedAnilib#capability(CapabilityKey)
 */
public record CapabilityKey<T>(ComponentId id, Class<T> type) implements Comparable<CapabilityKey<?>> {
    /**
     * Creates a capability key.
     *
     * @throws NullPointerException if {@code id} or {@code type} is
     *                              {@code null}
     */
    public CapabilityKey {
        Preconditions.requireNonNull(id, "id");
        Preconditions.requireNonNull(type, "type");
    }

    /**
     * Creates a capability key from its textual identifier and value type.
     *
     * @param id   the stable textual identity accepted by
     *             {@link ComponentId#of(String)}
     * @param type the runtime type of capability values
     * @param <T>  the exposed capability type
     * @return a key containing the parsed identifier and supplied type
     * @throws NullPointerException if {@code id} or {@code type} is
     *                              {@code null}
     * @throws IllegalArgumentException if {@code id} is blank or does not have
     *                                  the required component-identifier form
     */
    public static <T> CapabilityKey<T> of(String id, Class<T> type) {
        return new CapabilityKey<>(ComponentId.of(id), type);
    }

    /**
     * Compares keys first by identifier and then by the fully qualified name of
     * their value type.
     *
     * @param other the key to compare with
     * @return a negative integer, zero, or a positive integer as this key is
     *         less than, equal to, or greater than {@code other}
     * @throws NullPointerException if {@code other} is {@code null}
     */
    @Override
    public int compareTo(CapabilityKey<?> other) {
        int idComparison = id.compareTo(other.id);
        return idComparison != 0 ? idComparison : type.getName().compareTo(other.type.getName());
    }

    /**
     * Returns a diagnostic representation consisting of the identifier and the
     * simple name of the capability type.
     *
     * @return a string in the form {@code id<Type>}
     */
    @Override
    public String toString() {
        return id + "<" + type.getSimpleName() + ">";
    }
}
