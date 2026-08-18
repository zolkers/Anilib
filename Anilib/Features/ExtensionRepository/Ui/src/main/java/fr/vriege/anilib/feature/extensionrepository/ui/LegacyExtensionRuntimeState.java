package fr.vriege.anilib.feature.extensionrepository.ui;

/** Safe activation stage reached by one discovered legacy Android extension. */
public enum LegacyExtensionRuntimeState {
    UNSUPPORTED_PLATFORM,
    INCOMPATIBLE_METADATA,
    TRUST_REQUIRED,
    HOST_ABI_MISSING,
    HOST_ABI_AVAILABLE
}
