package fr.vriege.anilib.feature.extensionrepository.runtime;

import fr.vriege.anilib.feature.extensionrepository.ExtensionArtifactFormat;
import fr.vriege.anilib.feature.extensionrepository.ExtensionArtifactMetadata;
import fr.vriege.anilib.feature.extensionrepository.ExtensionContentKind;
import fr.vriege.anilib.feature.extensionrepository.ExtensionPackageMetadata;
import fr.vriege.anilib.feature.extensionrepository.ExtensionSourceMetadata;
import fr.vriege.anilib.foundation.validation.Preconditions;

import java.math.BigDecimal;
import java.net.URI;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Parses Aniyomi repository indexes plus the optional portable Anilib artifact block. */
public final class AniyomiRepositoryIndexParser {
    private static final int MAX_INDEX_CHARACTERS = 4 * 1024 * 1024;
    private static final int MAX_PACKAGES = 10_000;
    private static final int MAX_SOURCES_PER_PACKAGE = 100;

    public AniyomiRepositoryIndexParser() {
    }

    public List<ExtensionPackageMetadata> parse(URI indexUri, String json) {
        URI repository = requireRepositoryUri(indexUri);
        String content = Preconditions.requireNonNull(json, "json");
        if (content.length() > MAX_INDEX_CHARACTERS) {
            throw new IllegalArgumentException("Extension repository index exceeds 4 MiB");
        }
        List<Object> entries = array(JsonParser.parse(content), "repository root");
        if (entries.size() > MAX_PACKAGES) {
            throw new IllegalArgumentException("Extension repository contains too many packages");
        }
        List<ExtensionPackageMetadata> packages = new ArrayList<>();
        Set<String> packageNames = new HashSet<>();
        for (Object entry : entries) {
            ExtensionPackageMetadata metadata = packageMetadata(repository, object(entry, "package"));
            if (!packageNames.add(metadata.packageName())) {
                throw new IllegalArgumentException("Duplicate extension package: " + metadata.packageName());
            }
            packages.add(metadata);
        }
        packages.sort(Comparator.comparing(ExtensionPackageMetadata::packageName));
        return List.copyOf(packages);
    }

    private ExtensionPackageMetadata packageMetadata(URI repository, Map<String, Object> entry) {
        String packageName = string(entry, "pkg");
        List<ExtensionArtifactMetadata> artifacts = artifacts(repository, entry);
        List<Object> sourceEntries = array(required(entry, "sources"), "sources");
        if (sourceEntries.size() > MAX_SOURCES_PER_PACKAGE) {
            throw new IllegalArgumentException("Extension package contains too many sources: " + packageName);
        }
        List<ExtensionSourceMetadata> sources = sourceEntries.stream()
                .map(value -> sourceMetadata(object(value, "source")))
                .toList();
        return new ExtensionPackageMetadata(
                string(entry, "name"),
                packageName,
                string(entry, "lang"),
                integer(entry, "code"),
                string(entry, "version"),
                adult(entry.get("nsfw")),
                contentKind(packageName, entry.get("anilib")),
                sources,
                artifacts);
    }

    private List<ExtensionArtifactMetadata> artifacts(URI repository, Map<String, Object> entry) {
        List<ExtensionArtifactMetadata> artifacts = new ArrayList<>();
        optionalString(entry, "apk").ifPresent(value -> artifacts.add(new ExtensionArtifactMetadata(
                ExtensionArtifactFormat.ANIYOMI_APK,
                repository.resolve(value),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty())));
        Object anilibValue = entry.get("anilib");
        if (anilibValue != null) {
            Map<String, Object> anilib = object(anilibValue, "anilib");
            artifacts.add(new ExtensionArtifactMetadata(
                    ExtensionArtifactFormat.ANILIB_BUNDLE,
                    repository.resolve(string(anilib, "bundle")),
                    optionalString(anilib, "sha256"),
                    optionalString(anilib, "signature"),
                    optionalString(anilib, "keyId"),
                    optionalString(anilib, "api")));
        }
        if (artifacts.isEmpty()) {
            throw new IllegalArgumentException("Extension package must declare apk or anilib.bundle");
        }
        return List.copyOf(artifacts);
    }

    private ExtensionSourceMetadata sourceMetadata(Map<String, Object> source) {
        Optional<URI> baseUri = optionalStringAllowBlank(source, "baseUrl")
                .filter(value -> !value.isBlank())
                .map(URI::create);
        return new ExtensionSourceMetadata(
                string(source, "name"),
                string(source, "lang"),
                string(source, "id"),
                baseUri);
    }

    private ExtensionContentKind contentKind(String packageName, Object anilibValue) {
        if (anilibValue != null) {
            Optional<String> declared = optionalString(object(anilibValue, "anilib"), "kind");
            if (declared.isPresent()) {
                try {
                    return ExtensionContentKind.valueOf(declared.orElseThrow().toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException exception) {
                    throw new IllegalArgumentException("Unsupported anilib.kind", exception);
                }
            }
        }
        if (packageName.contains(".animeextension.")) {
            return ExtensionContentKind.ANIME;
        }
        if (packageName.contains(".extension.")) {
            return ExtensionContentKind.MANGA;
        }
        return ExtensionContentKind.UNKNOWN;
    }

    private boolean adult(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }
        if (value instanceof Long longValue && (longValue == 0 || longValue == 1)) {
            return longValue == 1;
        }
        throw new IllegalArgumentException("nsfw must be boolean, 0, or 1");
    }

    private long integer(Map<String, Object> object, String name) {
        Object value = required(object, name);
        if (value instanceof Long longValue) {
            return longValue;
        }
        if (value instanceof BigDecimal decimal) {
            try {
                return decimal.longValueExact();
            } catch (ArithmeticException exception) {
                throw new IllegalArgumentException(name + " must be an integer", exception);
            }
        }
        throw new IllegalArgumentException(name + " must be an integer");
    }

    private String string(Map<String, Object> object, String name) {
        Object value = required(object, name);
        if (!(value instanceof String text)) {
            throw new IllegalArgumentException(name + " must be a string");
        }
        return Preconditions.requireNonBlank(text, name);
    }

    private Optional<String> optionalString(Map<String, Object> object, String name) {
        Object value = object.get(name);
        if (value == null) {
            return Optional.empty();
        }
        if (!(value instanceof String text)) {
            throw new IllegalArgumentException(name + " must be a string");
        }
        return Optional.of(Preconditions.requireNonBlank(text, name));
    }

    private Optional<String> optionalStringAllowBlank(Map<String, Object> object, String name) {
        Object value = object.get(name);
        if (value == null) {
            return Optional.empty();
        }
        if (!(value instanceof String text)) {
            throw new IllegalArgumentException(name + " must be a string");
        }
        return Optional.of(text);
    }

    private Object required(Map<String, Object> object, String name) {
        Object value = object.get(name);
        if (value == null) {
            throw new IllegalArgumentException("Missing repository field: " + name);
        }
        return value;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> object(Object value, String name) {
        if (!(value instanceof Map<?, ?>)) {
            throw new IllegalArgumentException(name + " must be an object");
        }
        return (Map<String, Object>) value;
    }

    @SuppressWarnings("unchecked")
    private List<Object> array(Object value, String name) {
        if (!(value instanceof List<?>)) {
            throw new IllegalArgumentException(name + " must be an array");
        }
        return (List<Object>) value;
    }

    static URI requireRepositoryUri(URI value) {
        URI uri = Preconditions.requireNonNull(value, "indexUri").normalize();
        if (!"https".equalsIgnoreCase(uri.getScheme())
                || uri.getHost() == null
                || uri.getHost().isBlank()
                || uri.getUserInfo() != null
                || uri.getFragment() != null) {
            throw new IllegalArgumentException("repository index must be an absolute HTTPS URI without credentials");
        }
        return uri;
    }
}
