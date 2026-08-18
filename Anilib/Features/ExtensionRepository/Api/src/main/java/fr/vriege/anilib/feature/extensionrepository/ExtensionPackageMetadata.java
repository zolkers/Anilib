package fr.vriege.anilib.feature.extensionrepository;

import fr.vriege.anilib.foundation.validation.Preconditions;

import java.net.URI;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

public record ExtensionPackageMetadata(
        String displayName,
        String packageName,
        String languageTag,
        long versionCode,
        String versionName,
        boolean adult,
        ExtensionContentKind contentKind,
        List<ExtensionSourceMetadata> sources,
        List<ExtensionArtifactMetadata> artifacts,
        Optional<String> changelog,
        Optional<URI> icon) {
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
        changelog = Preconditions.requireNonNull(changelog, "changelog")
                .map(value -> Preconditions.requireNonBlank(value, "changelog"));
        icon = Preconditions.requireNonNull(icon, "icon").map(ExtensionPackageMetadata::requireIconUri);
        if (changelog.map(String::length).orElse(0) > 20_000) {
            throw new IllegalArgumentException("changelog must not exceed 20000 characters");
        }
        Set<ExtensionArtifactFormat> formats = new HashSet<>();
        for (ExtensionArtifactMetadata artifact : artifacts) {
            if (!formats.add(artifact.format())) {
                throw new IllegalArgumentException("extension package cannot duplicate an artifact format");
            }
        }
    }

    public ExtensionPackageMetadata(
            String displayName,
            String packageName,
            String languageTag,
            long versionCode,
            String versionName,
            boolean adult,
            ExtensionContentKind contentKind,
            List<ExtensionSourceMetadata> sources,
            List<ExtensionArtifactMetadata> artifacts) {
        this(
                displayName,
                packageName,
                languageTag,
                versionCode,
                versionName,
                adult,
                contentKind,
                sources,
                artifacts,
                Optional.empty(),
                Optional.empty());
    }

    public ExtensionPackageMetadata(
            String displayName,
            String packageName,
            String languageTag,
            long versionCode,
            String versionName,
            boolean adult,
            ExtensionContentKind contentKind,
            List<ExtensionSourceMetadata> sources,
            List<ExtensionArtifactMetadata> artifacts,
            Optional<String> changelog) {
        this(
                displayName,
                packageName,
                languageTag,
                versionCode,
                versionName,
                adult,
                contentKind,
                sources,
                artifacts,
                changelog,
                Optional.empty());
    }

    private static URI requireIconUri(URI value) {
        URI iconUri = Preconditions.requireNonNull(value, "icon URI");
        String scheme = iconUri.getScheme();
        if (!iconUri.isAbsolute() || iconUri.getHost() == null
                || !(scheme.equalsIgnoreCase("https") || scheme.equalsIgnoreCase("http"))) {
            throw new IllegalArgumentException("icon URI must be an absolute HTTP(S) URI");
        }
        return iconUri;
    }

    private static String normalizeLanguage(String value) {
        String language = Preconditions.requireNonBlank(value, "languageTag").replace('_', '-');
        return language.equalsIgnoreCase("all") ? "und" : language.toLowerCase(Locale.ROOT);
    }
}
