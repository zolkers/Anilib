package fr.vriege.anilib.feature.extensionrepository;

import fr.vriege.anilib.foundation.validation.Preconditions;

import java.util.List;
import java.util.Map;

public record ExtensionUpdateResult(
        List<InstalledExtensionPackage> updated,
        Map<String, String> failures) {
    public ExtensionUpdateResult {
        updated = List.copyOf(Preconditions.requireNonNull(updated, "updated"));
        failures = Map.copyOf(Preconditions.requireNonNull(failures, "failures"));
    }
}
