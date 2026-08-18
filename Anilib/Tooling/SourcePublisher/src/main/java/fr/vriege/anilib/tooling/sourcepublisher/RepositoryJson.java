package fr.vriege.anilib.tooling.sourcepublisher;

import java.net.URI;
import java.util.List;

/** Deterministic JSON encoding for the supported repository index shape. */
final class RepositoryJson {
    private RepositoryJson() {
    }

    static String minified(List<RepositoryPublisher.PublishedPackage> packages) {
        return encode(packages, false) + System.lineSeparator();
    }

    static String pretty(List<RepositoryPublisher.PublishedPackage> packages) {
        return encode(packages, true) + System.lineSeparator();
    }

    private static String encode(List<RepositoryPublisher.PublishedPackage> packages, boolean pretty) {
        StringBuilder json = new StringBuilder("[");
        for (int index = 0; index < packages.size(); index++) {
            if (index > 0) {
                json.append(',');
            }
            line(json, pretty, 1);
            packageObject(json, packages.get(index), pretty, 1);
        }
        if (!packages.isEmpty()) {
            line(json, pretty, 0);
        }
        return json.append(']').toString();
    }

    private static void packageObject(
            StringBuilder json,
            RepositoryPublisher.PublishedPackage published,
            boolean pretty,
            int depth) {
        SourcePackageConfiguration value = published.configuration();
        json.append('{');
        field(json, "name", value.displayName(), pretty, depth + 1, false);
        field(json, "pkg", value.packageName(), pretty, depth + 1, true);
        field(json, "lang", value.language(), pretty, depth + 1, true);
        number(json, "code", value.versionCode(), pretty, depth + 1);
        field(json, "version", value.versionName(), pretty, depth + 1, true);
        bool(json, "nsfw", value.adult(), pretty, depth + 1);
        line(json.append(','), pretty, depth + 1);
        quote(json, "anilib").append(pretty ? ": {" : ":{");
        field(json, "bundle", value.artifactName(), pretty, depth + 2, false);
        field(json, "api", value.apiVersion(), pretty, depth + 2, true);
        field(json, "sha256", published.sha256(), pretty, depth + 2, true);
        field(json, "signature", published.signature(), pretty, depth + 2, true);
        field(json, "keyId", value.keyId(), pretty, depth + 2, true);
        field(json, "kind", value.kind(), pretty, depth + 2, true);
        line(json, pretty, depth + 1);
        json.append("},");
        line(json, pretty, depth + 1);
        quote(json, "sources").append(pretty ? ": [" : ":[");
        for (int index = 0; index < value.sources().size(); index++) {
            if (index > 0) {
                json.append(',');
            }
            line(json, pretty, depth + 2);
            sourceObject(json, value.sources().get(index), pretty, depth + 2);
        }
        line(json, pretty, depth + 1);
        json.append(']');
        line(json, pretty, depth);
        json.append('}');
    }

    private static void sourceObject(
            StringBuilder json,
            SourcePackageConfiguration.SourceEntry source,
            boolean pretty,
            int depth) {
        json.append('{');
        field(json, "name", source.name(), pretty, depth + 1, false);
        field(json, "lang", source.language(), pretty, depth + 1, true);
        field(json, "id", source.id(), pretty, depth + 1, true);
        URI baseUri = source.baseUri();
        if (baseUri != null) {
            field(json, "baseUrl", baseUri.toASCIIString(), pretty, depth + 1, true);
        }
        line(json, pretty, depth);
        json.append('}');
    }

    private static void field(
            StringBuilder json,
            String name,
            String value,
            boolean pretty,
            int depth,
            boolean comma) {
        if (comma) {
            json.append(',');
        }
        line(json, pretty, depth);
        quote(json, name).append(pretty ? ": " : ":");
        quote(json, value);
    }

    private static void number(StringBuilder json, String name, long value, boolean pretty, int depth) {
        json.append(',');
        line(json, pretty, depth);
        quote(json, name).append(pretty ? ": " : ":").append(value);
    }

    private static void bool(StringBuilder json, String name, boolean value, boolean pretty, int depth) {
        json.append(',');
        line(json, pretty, depth);
        quote(json, name).append(pretty ? ": " : ":").append(value);
    }

    private static void line(StringBuilder json, boolean pretty, int depth) {
        if (pretty) {
            json.append(System.lineSeparator()).append("  ".repeat(depth));
        }
    }

    private static StringBuilder quote(StringBuilder json, String value) {
        json.append('"');
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            switch (character) {
                case '"' -> json.append("\\\"");
                case '\\' -> json.append("\\\\");
                case '\b' -> json.append("\\b");
                case '\f' -> json.append("\\f");
                case '\n' -> json.append("\\n");
                case '\r' -> json.append("\\r");
                case '\t' -> json.append("\\t");
                default -> {
                    if (character < 0x20) {
                        json.append(String.format("\\u%04x", (int) character));
                    } else {
                        json.append(character);
                    }
                }
            }
        }
        return json.append('"');
    }
}
