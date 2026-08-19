package fr.vriege.anilib.platform.desktopextensionhost.extension;

import java.util.Objects;

public record LoadedSource(
        long id,
        String name,
        String language,
        String packageName,
        ExtensionKind kind,
        Object instance) {
    public LoadedSource {
        name = requireText(name, "name");
        language = requireText(language, "language");
        packageName = requireText(packageName, "packageName");
        kind = Objects.requireNonNull(kind, "kind");
        instance = Objects.requireNonNull(instance, "instance");
    }

    private static String requireText(String value, String label) {
        String result = Objects.requireNonNull(value, label).strip();
        if (result.isEmpty()) {
            throw new IllegalArgumentException(label + " must not be blank");
        }
        return result;
    }
}
