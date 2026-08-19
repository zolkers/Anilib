package fr.vriege.anilib.feature.extensionrepository;

import fr.vriege.anilib.foundation.validation.Preconditions;

import java.net.URI;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

public record ExtensionSourceMetadata(
        String displayName,
        String languageTag,
        String sourceId,
        Optional<URI> baseUri) {
    public ExtensionSourceMetadata {
        displayName = Preconditions.requireNonBlank(displayName, "displayName");
        languageTag = normalizeLanguage(languageTag);
        sourceId = Preconditions.requireNonBlank(sourceId, "sourceId");
        baseUri = Preconditions.requireNonNull(baseUri, "baseUri").map(ExtensionSourceMetadata::normalizeBaseUri);
    }

    public Set<String> runtimeSourceIds() {
        LinkedHashSet<String> identities = new LinkedHashSet<>();
        identities.add(sourceId);
        numericSourceId(sourceId).ifPresent(value -> identities.add("aniyomi." + Long.toUnsignedString(value)));
        return Set.copyOf(identities);
    }

    private static Optional<Long> numericSourceId(String value) {
        try {
            return Optional.of(value.startsWith("-")
                    ? Long.parseLong(value)
                    : Long.parseUnsignedLong(value));
        } catch (NumberFormatException ignored) {
            return Optional.empty();
        }
    }

    private static String normalizeLanguage(String value) {
        String language = Preconditions.requireNonBlank(value, "languageTag").replace('_', '-');
        return language.equalsIgnoreCase("all") ? "und" : language.toLowerCase(Locale.ROOT);
    }

    private static URI normalizeBaseUri(URI value) {
        URI uri = Preconditions.requireNonNull(value, "baseUri").normalize();
        if (!("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))
                || uri.getHost() == null
                || uri.getHost().isBlank()
                || uri.getUserInfo() != null
                || uri.getFragment() != null) {
            throw new IllegalArgumentException("baseUri must be an absolute HTTP(S) URI without credentials");
        }
        return uri;
    }
}
