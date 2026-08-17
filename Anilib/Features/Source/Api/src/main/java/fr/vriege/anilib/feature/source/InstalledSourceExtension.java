package fr.vriege.anilib.feature.source;

import fr.vriege.anilib.foundation.validation.Preconditions;

/** Observable installed extension metadata exposed to the shared Browse UI. */
public record InstalledSourceExtension(
        SourceExtensionManifest manifest,
        SourceDescriptor source) {
    public InstalledSourceExtension {
        Preconditions.requireNonNull(manifest, "manifest");
        Preconditions.requireNonNull(source, "source");
        if (!manifest.sourceId().equals(source.id())) {
            throw new IllegalArgumentException("extension manifest and source descriptor IDs must match");
        }
    }
}
