package fr.vriege.anilib.feature.extensionrepository.runtime;

import java.net.URI;
import java.util.List;
import java.util.Locale;

final class ExtensionRepositoryLocations {
    private static final String GITHUB_HOST = "github.com";
    private static final String RAW_GITHUB_HOST = "raw.githubusercontent.com";

    private ExtensionRepositoryLocations() {
    }

    static List<URI> indexCandidates(URI configuredLocation) {
        URI location = AniyomiRepositoryIndexParser.requireRepositoryUri(configuredLocation);
        if (!GITHUB_HOST.equals(location.getHost().toLowerCase(Locale.ROOT))) {
            return List.of(location);
        }
        if (location.getQuery() != null) {
            throw new IllegalArgumentException("GitHub repository URLs must not contain a query");
        }
        String[] segments = location.getPath().split("/");
        if (segments.length != 3 || segments[1].isBlank() || segments[2].isBlank()) {
            return List.of(location);
        }
        String owner = requireGitHubSegment(segments[1], "owner");
        String repository = requireGitHubSegment(stripGitSuffix(segments[2]), "repository");
        String root = "https://" + RAW_GITHUB_HOST + "/" + owner + "/" + repository + "/HEAD/";
        return List.of(
                URI.create(root + "index.min.json"),
                URI.create(root + "index.json"),
                URI.create(root.replace("/HEAD/", "/repo/") + "index.min.json"),
                URI.create(root.replace("/HEAD/", "/repo/") + "index.json"));
    }

    private static String stripGitSuffix(String repository) {
        return repository.endsWith(".git")
                ? repository.substring(0, repository.length() - ".git".length())
                : repository;
    }

    private static String requireGitHubSegment(String value, String label) {
        if (!value.matches("[A-Za-z0-9_.-]+") || value.equals(".") || value.equals("..")) {
            throw new IllegalArgumentException("Invalid GitHub " + label);
        }
        return value;
    }
}
