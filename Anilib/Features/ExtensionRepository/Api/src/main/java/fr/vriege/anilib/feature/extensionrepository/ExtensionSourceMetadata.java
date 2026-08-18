package fr.vriege.anilib.feature.extensionrepository;

import fr.vriege.anilib.foundation.validation.Preconditions;

import java.net.URI;
import java.util.Locale;
import java.util.Optional;

/** Source identity copied from one Aniyomi-compatible repository entry. */
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
