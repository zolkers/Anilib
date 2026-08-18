package fr.vriege.anilib.feature.extensionrepository;

import fr.vriege.anilib.foundation.validation.Preconditions;

/** Validation for repository package identities, which are intentionally opaque to Anilib. */
public final class ExtensionPackageIdentifiers {
    private static final int MAX_CHARACTERS = 512;

    private ExtensionPackageIdentifiers() {
    }

    public static String requireValid(String value) {
        String identifier = Preconditions.requireNonBlank(value, "packageName");
        if (identifier.length() > MAX_CHARACTERS) {
            throw new IllegalArgumentException("packageName must not exceed " + MAX_CHARACTERS + " characters");
        }
        for (int index = 0; index < identifier.length(); index++) {
            char character = identifier.charAt(index);
            if (Character.isISOControl(character) || Character.isSurrogate(character)) {
                throw new IllegalArgumentException("packageName must contain printable Unicode text");
            }
        }
        return identifier;
    }
}
