package fr.vriege.anilib.kernel;

import fr.vriege.anilib.foundation.component.ComponentDescriptor;
import fr.vriege.anilib.foundation.validation.Preconditions;

import java.util.Set;
import java.util.LinkedHashSet;

/**
 * The immutable, side-effect-free declaration of an {@link AnilibPlugin}.
 *
 * <p>The manifest is the sole input used by {@link PluginEngine} to validate
 * and order a product graph before installation. A required capability creates
 * a dependency on its unique provider. Provided capabilities become immutable
 * members of the started product graph, while contribution points authorize
 * the plugin to add zero or more ordered values during installation.</p>
 *
 * <p>The three sets are defensively copied. Their iteration order is not part
 * of this contract.</p>
 *
 * @param descriptor           the identity and display metadata of the plugin
 * @param requiredCapabilities capabilities that must be available before this
 *                             plugin is installed
 * @param providedCapabilities capabilities that this plugin promises to
 *                             publish during installation
 * @param contributionPoints   points to which this plugin is authorized to
 *                             contribute
 *
 * @see #builder(ComponentDescriptor)
 * @see PluginInstallationContext
 */
public record PluginManifest(
        ComponentDescriptor descriptor,
        Set<CapabilityKey<?>> requiredCapabilities,
        Set<CapabilityKey<?>> providedCapabilities,
        Set<ContributionPoint<?>> contributionPoints) {

    /**
     * Creates an immutable plugin manifest.
     *
     * @throws NullPointerException if any argument or any element of a supplied
     *                              set is {@code null}
     * @throws IllegalArgumentException if the same capability is both required
     *                                  and provided
     */
    public PluginManifest {
        Preconditions.requireNonNull(descriptor, "descriptor");
        requiredCapabilities = Set.copyOf(requiredCapabilities);
        providedCapabilities = Set.copyOf(providedCapabilities);
        contributionPoints = Set.copyOf(contributionPoints);

        for (CapabilityKey<?> key : requiredCapabilities) {
            if (providedCapabilities.contains(key)) {
                throw new IllegalArgumentException("A plugin cannot require and provide " + key);
            }
        }
    }

    /**
     * Creates a builder for a plugin with the supplied descriptor.
     *
     * @param descriptor the non-null plugin descriptor
     * @return a new, initially empty manifest builder
     * @throws NullPointerException if {@code descriptor} is {@code null}
     */
    public static Builder builder(ComponentDescriptor descriptor) {
        return new Builder(descriptor);
    }

    /**
     * Incrementally assembles a {@link PluginManifest}.
     *
     * <p>The builder stores declarations as sets; adding the same declaration
     * more than once has no additional effect. A builder may be reused to
     * create successive immutable snapshots.</p>
     */
    public static final class Builder {
        private final ComponentDescriptor descriptor;
        private final Set<CapabilityKey<?>> required = new LinkedHashSet<>();
        private final Set<CapabilityKey<?>> provided = new LinkedHashSet<>();
        private final Set<ContributionPoint<?>> contributions = new LinkedHashSet<>();

        private Builder(ComponentDescriptor descriptor) {
            this.descriptor = Preconditions.requireNonNull(descriptor, "descriptor");
        }

        /**
         * Declares a capability that must be installed before this plugin.
         *
         * @param key the non-null required capability key
         * @return this builder
         * @throws NullPointerException if {@code key} is {@code null}
         */
        public Builder requires(CapabilityKey<?> key) {
            required.add(Preconditions.requireNonNull(key, "key"));
            return this;
        }

        /**
         * Declares a capability that this plugin will publish during
         * installation.
         *
         * @param key the non-null provided capability key
         * @return this builder
         * @throws NullPointerException if {@code key} is {@code null}
         */
        public Builder provides(CapabilityKey<?> key) {
            provided.add(Preconditions.requireNonNull(key, "key"));
            return this;
        }

        /**
         * Declares a contribution point that this plugin may extend during
         * installation.
         *
         * @param point the non-null contribution point
         * @return this builder
         * @throws NullPointerException if {@code point} is {@code null}
         */
        public Builder contributesTo(ContributionPoint<?> point) {
            contributions.add(Preconditions.requireNonNull(point, "point"));
            return this;
        }

        /**
         * Creates an immutable manifest from the current declarations.
         *
         * @return a new plugin manifest
         * @throws IllegalArgumentException if a capability has been declared as
         *                                  both required and provided
         */
        public PluginManifest build() {
            return new PluginManifest(descriptor, required, provided, contributions);
        }
    }
}
