package fr.vriege.anilib.platform.desktopengine.extension;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record ExtensionApkMetadata(
        String packageName,
        String displayName,
        String versionName,
        long versionCode,
        ExtensionKind kind,
        boolean adult,
        List<String> sourceClasses,
        Optional<String> factoryClass) {
    public ExtensionApkMetadata {
        packageName = requireText(packageName, "packageName");
        displayName = requireText(displayName, "displayName");
        versionName = requireText(versionName, "versionName");
        kind = Objects.requireNonNull(kind, "kind");
        sourceClasses = List.copyOf(Objects.requireNonNull(sourceClasses, "sourceClasses"));
        factoryClass = Objects.requireNonNull(factoryClass, "factoryClass");
        if (versionCode < 0) {
            throw new IllegalArgumentException("versionCode must not be negative");
        }
        if (sourceClasses.isEmpty() && factoryClass.isEmpty()) {
            throw new IllegalArgumentException("Extension does not declare a source class or factory");
        }
    }

    private static String requireText(String value, String name) {
        String result = Objects.requireNonNull(value, name).strip();
        if (result.isEmpty()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return result;
    }
}
