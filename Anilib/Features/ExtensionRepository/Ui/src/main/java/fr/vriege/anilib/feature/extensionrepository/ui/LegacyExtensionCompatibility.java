package fr.vriege.anilib.feature.extensionrepository.ui;

/** Metadata-level compatibility of one Android-installed Aniyomi extension APK. */
public enum LegacyExtensionCompatibility {
    COMPATIBLE_METADATA,
    UNSUPPORTED_LIBRARY,
    MISSING_ENTRYPOINT,
    UNSIGNED
}
