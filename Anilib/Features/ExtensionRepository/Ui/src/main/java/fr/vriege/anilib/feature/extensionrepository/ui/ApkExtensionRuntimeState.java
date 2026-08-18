package fr.vriege.anilib.feature.extensionrepository.ui;

public enum ApkExtensionRuntimeState {
    UNSUPPORTED_PLATFORM,
    INCOMPATIBLE_METADATA,
    TRUST_REQUIRED,
    HOST_ABI_MISSING,
    HOST_ABI_AVAILABLE,
    ACTIVATION_FAILED,
    ACTIVE
}
