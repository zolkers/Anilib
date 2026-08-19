package fr.vriege.anilib.kernel;

import fr.vriege.anilib.foundation.component.ComponentId;
import fr.vriege.anilib.foundation.validation.Preconditions;

/**
 * A stable, type-safe coordinate to which plugins may contribute values.
 *
 * <p>Unlike a {@link CapabilityKey}, a contribution point accepts zero or more
 * values from independently selected plugins. Its identifier and value type
 * together define its identity and natural ordering. A plugin must declare the
 * point in its {@link PluginManifest} before contributing to it.</p>
 *
 * @param <T>  the accepted contribution type
 * @param id   the stable identity of the contribution point
 * @param type the runtime type used to validate contribution values
 *
 * @see PluginManifest#contributionPoints()
 * @see PluginInstallationContext#contribute(ContributionPoint, int, Object)
 * @see StartedAnilib#contributions(ContributionPoint)
 */
public record ContributionPoint<T>(ComponentId id, Class<T> type) implements Comparable<ContributionPoint<?>> {
    /**
     * Creates a contribution point.
     *
     * @throws NullPointerException if {@code id} or {@code type} is
     *                              {@code null}
     */
    public ContributionPoint {
        Preconditions.requireNonNull(id, "id");
        Preconditions.requireNonNull(type, "type");
    }

    /**
     * Creates a contribution point from its textual identifier and value type.
     *
     * @param id   the stable textual identity accepted by
     *             {@link ComponentId#of(String)}
     * @param type the runtime type of contribution values
     * @param <T>  the accepted contribution type
     * @return a point containing the parsed identifier and supplied type
     * @throws NullPointerException if {@code id} or {@code type} is
     *                              {@code null}
     * @throws IllegalArgumentException if {@code id} is blank or does not have
     *                                  the required component-identifier form
     */
    public static <T> ContributionPoint<T> of(String id, Class<T> type) {
        return new ContributionPoint<>(ComponentId.of(id), type);
    }

    /**
     * Compares points first by identifier and then by the fully qualified name
     * of their value type.
     *
     * @param other the point to compare with
     * @return a negative integer, zero, or a positive integer as this point is
     *         less than, equal to, or greater than {@code other}
     * @throws NullPointerException if {@code other} is {@code null}
     */
    @Override
    public int compareTo(ContributionPoint<?> other) {
        int idComparison = id.compareTo(other.id);
        return idComparison != 0 ? idComparison : type.getName().compareTo(other.type.getName());
    }

    /**
     * Returns a diagnostic representation consisting of the identifier and the
     * simple name of the contribution type.
     *
     * @return a string in the form {@code id<Type>}
     */
    @Override
    public String toString() {
        return id + "<" + type.getSimpleName() + ">";
    }
}
