package fr.vriege.anilib.feature.tracker;

import java.util.Objects;

/** Current authentication state shown by the shared tracking settings screen. */
public record TrackerAccount(TrackerDescriptor descriptor, boolean authenticated, String accountName) {
    public TrackerAccount {
        Objects.requireNonNull(descriptor, "descriptor must not be null");
        accountName = Objects.requireNonNull(accountName, "accountName must not be null");
    }
}
