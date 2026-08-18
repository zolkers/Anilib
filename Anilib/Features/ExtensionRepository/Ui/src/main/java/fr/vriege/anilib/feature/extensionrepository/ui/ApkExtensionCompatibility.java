package fr.vriege.anilib.feature.extensionrepository.ui;

/** Metadata-level compatibility of one Android-installed extension APK. */
public enum ApkExtensionCompatibility {
    COMPATIBLE_METADATA,
    UNSUPPORTED_LIBRARY,
    MISSING_ENTRYPOINT,
    UNSIGNED
}
