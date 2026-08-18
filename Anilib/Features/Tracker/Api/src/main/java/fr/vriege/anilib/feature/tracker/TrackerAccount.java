package fr.vriege.anilib.feature.tracker;

import java.util.Objects;

public record TrackerAccount(TrackerDescriptor descriptor, boolean authenticated, String accountName) {
    public TrackerAccount {
        Objects.requireNonNull(descriptor, "descriptor must not be null");
        accountName = Objects.requireNonNull(accountName, "accountName must not be null");
    }
}
