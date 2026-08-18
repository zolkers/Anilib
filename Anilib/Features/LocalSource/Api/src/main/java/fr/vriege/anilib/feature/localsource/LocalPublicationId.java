package fr.vriege.anilib.feature.localsource;

import fr.vriege.anilib.foundation.validation.Preconditions;

public record LocalPublicationId(LocalPublicationType type, String relativePath) {
    public LocalPublicationId {
        Preconditions.requireNonNull(type, "type");
        validateRelativePath(relativePath, "relativePath");
    }

    static void validateRelativePath(String value, String name) {
        Preconditions.requireNonBlank(value, name);
        if (value.startsWith("/") || value.startsWith("\\") || value.contains("\\")) {
            throw new IllegalArgumentException(name + " must use a relative forward-slash path");
        }
        for (String segment : value.split("/", -1)) {
            if (segment.isBlank() || segment.equals(".") || segment.equals("..")) {
                throw new IllegalArgumentException(name + " contains an unsafe path segment");
            }
        }
    }
}
