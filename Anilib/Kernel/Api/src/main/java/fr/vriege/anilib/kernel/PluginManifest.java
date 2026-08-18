package fr.vriege.anilib.kernel;

import fr.vriege.anilib.foundation.component.ComponentDescriptor;
import fr.vriege.anilib.foundation.validation.Preconditions;

import java.util.Set;

public record PluginManifest(
        ComponentDescriptor descriptor,
        Set<CapabilityKey<?>> requiredCapabilities,
        Set<CapabilityKey<?>> providedCapabilities,
        Set<ContributionPoint<?>> contributionPoints) {

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

    public static Builder builder(ComponentDescriptor descriptor) {
        return new Builder(descriptor);
    }

    public static final class Builder {
        private final ComponentDescriptor descriptor;
        private final java.util.Set<CapabilityKey<?>> required = new java.util.LinkedHashSet<>();
        private final java.util.Set<CapabilityKey<?>> provided = new java.util.LinkedHashSet<>();
        private final java.util.Set<ContributionPoint<?>> contributions = new java.util.LinkedHashSet<>();

        private Builder(ComponentDescriptor descriptor) {
            this.descriptor = Preconditions.requireNonNull(descriptor, "descriptor");
        }

        public Builder requires(CapabilityKey<?> key) {
            required.add(Preconditions.requireNonNull(key, "key"));
            return this;
        }

        public Builder provides(CapabilityKey<?> key) {
            provided.add(Preconditions.requireNonNull(key, "key"));
            return this;
        }

        public Builder contributesTo(ContributionPoint<?> point) {
            contributions.add(Preconditions.requireNonNull(point, "point"));
            return this;
        }

        public PluginManifest build() {
            return new PluginManifest(descriptor, required, provided, contributions);
        }
    }
}
