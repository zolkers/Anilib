package fr.vriege.anilib.feature.extensionrepository.ui;

/** Safe activation stage reached by one discovered Android extension APK. */
public enum ApkExtensionRuntimeState {
    UNSUPPORTED_PLATFORM,
    INCOMPATIBLE_METADATA,
    TRUST_REQUIRED,
    HOST_ABI_MISSING,
    HOST_ABI_AVAILABLE,
    ACTIVATION_FAILED,
    ACTIVE
}
