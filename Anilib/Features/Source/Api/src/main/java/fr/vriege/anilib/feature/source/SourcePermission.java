package fr.vriege.anilib.feature.source;

/** Sensitive host capabilities that a selected source Bundle must request explicitly. */
public enum SourcePermission {
    NETWORK,
    CLEARTEXT_NETWORK,
    /** Trusted platform adapter whose I/O is owned outside the portable Source context. */
    TRUSTED_PLATFORM_RUNTIME
}
