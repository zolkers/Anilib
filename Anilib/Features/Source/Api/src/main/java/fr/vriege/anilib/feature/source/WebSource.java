package fr.vriege.anilib.feature.source;

import java.net.URI;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public interface WebSource extends Source {
    URI homePage();

    default Optional<URI> titlePage(SourceCatalogueItemId itemId) {
        return Optional.empty();
    }

    default SourceWebPage homeBrowserPage() {
        return browserPage(homePage());
    }

    default Optional<SourceWebPage> titleBrowserPage(SourceCatalogueItemId itemId) {
        return titlePage(itemId).map(this::browserPage);
    }

    default Map<String, String> browserHeaders(URI location) {
        return Map.of();
    }

    default Optional<String> browserUserAgent(URI location) {
        return Optional.empty();
    }

    default Set<String> browserCompletionCookies(URI location) {
        return Set.of();
    }

    private SourceWebPage browserPage(URI location) {
        return new SourceWebPage(
                location,
                browserHeaders(location),
                browserUserAgent(location),
                browserCompletionCookies(location));
    }
}
