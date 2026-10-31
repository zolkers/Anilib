package fr.vriege.anilib.platform.desktopextensionhost.extension;

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
        Optional<String> factoryClass,
        int extensionApiVersion,
        boolean torrent) {
    public ExtensionApkMetadata(
            String packageName,
            String displayName,
            String versionName,
            long versionCode,
            ExtensionKind kind,
            boolean adult,
            List<String> sourceClasses,
            Optional<String> factoryClass) {
        this(
                packageName,
                displayName,
                versionName,
                versionCode,
                kind,
                adult,
                sourceClasses,
                factoryClass,
                0,
                false);
    }

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
        if (extensionApiVersion < 0)
            throw new IllegalArgumentException("Extension API version must not be negative");
        if (!packageName.matches("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+")) {
            throw new IllegalArgumentException("Invalid extension package name: " + packageName);
        }
        if (sourceClasses.isEmpty() && factoryClass.isEmpty()) {
            throw new IllegalArgumentException(
                    "Extension does not declare a source class or factory");
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
