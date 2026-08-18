package fr.vriege.anilib.feature.extensionrepository;

import fr.vriege.anilib.foundation.validation.Preconditions;

/** One enabled portable Bundle that could not be selected during product startup. */
public record ExtensionBundleLoadFailure(String packageName, String message) {
    public ExtensionBundleLoadFailure {
        packageName = Preconditions.requireNonBlank(packageName, "packageName");
        message = Preconditions.requireNonBlank(message, "message");
    }
}
