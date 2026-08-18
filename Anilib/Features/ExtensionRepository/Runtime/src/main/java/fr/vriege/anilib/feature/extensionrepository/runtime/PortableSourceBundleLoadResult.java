package fr.vriege.anilib.feature.extensionrepository.runtime;

import fr.vriege.anilib.feature.extensionrepository.ExtensionBundleLoadFailure;
import fr.vriege.anilib.foundation.validation.Preconditions;
import fr.vriege.anilib.kernel.AnilibPlugin;

import java.util.List;

public record PortableSourceBundleLoadResult(
        List<AnilibPlugin> bundles,
        List<ExtensionBundleLoadFailure> failures) {
    public PortableSourceBundleLoadResult {
        bundles = List.copyOf(Preconditions.requireNonNull(bundles, "bundles"));
        failures = List.copyOf(Preconditions.requireNonNull(failures, "failures"));
    }
}
