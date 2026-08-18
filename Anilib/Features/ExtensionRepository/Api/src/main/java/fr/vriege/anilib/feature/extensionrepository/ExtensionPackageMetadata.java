package fr.vriege.anilib.feature.extensionrepository;

import fr.vriege.anilib.foundation.validation.Preconditions;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** One extension package advertised by a user-supplied repository. */
public record ExtensionPackageMetadata(
        String displayName,
        String packageName,
        String languageTag,
        long versionCode,
        String versionName,
        boolean adult,
        ExtensionContentKind contentKind,
        List<ExtensionSourceMetadata> sources,
        List<ExtensionArtifactMetadata> artifacts) {
    public ExtensionPackageMetadata {
        displayName = Preconditions.requireNonBlank(displayName, "displayName");
        packageName = ExtensionPackageIdentifiers.requireValid(packageName);
        languageTag = normalizeLanguage(languageTag);
        if (versionCode < 0) {
            throw new IllegalArgumentException("versionCode must not be negative");
        }
        versionName = Preconditions.requireNonBlank(versionName, "versionName");
        contentKind = Preconditions.requireNonNull(contentKind, "contentKind");
        sources = List.copyOf(Preconditions.requireNonNull(sources, "sources"));
        artifacts = List.copyOf(Preconditions.requireNonNull(artifacts, "artifacts"));
        if (sources.isEmpty() || artifacts.isEmpty()) {
            throw new IllegalArgumentException("extension package must declare sources and artifacts");
        }
        Set<ExtensionArtifactFormat> formats = new HashSet<>();
        for (ExtensionArtifactMetadata artifact : artifacts) {
            if (!formats.add(artifact.format())) {
                throw new IllegalArgumentException("extension package cannot duplicate an artifact format");
            }
        }
    }

    private static String normalizeLanguage(String value) {
        String language = Preconditions.requireNonBlank(value, "languageTag").replace('_', '-');
        return language.equalsIgnoreCase("all") ? "und" : language.toLowerCase(Locale.ROOT);
    }
}
