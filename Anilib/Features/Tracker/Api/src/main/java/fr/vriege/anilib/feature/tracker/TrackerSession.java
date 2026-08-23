package fr.vriege.anilib.feature.tracker;

import fr.vriege.anilib.foundation.validation.Preconditions;

import java.util.Objects;

public record TrackerSession(TrackerCredentials credentials, String accountName) {
    public TrackerSession {
        credentials = Objects.requireNonNull(credentials, "credentials must not be null");
        accountName = Preconditions.requireNonBlank(accountName, "accountName").strip();
        if (credentials.authentication() == TrackerAuthentication.USERNAME_PASSWORD) {
            throw new IllegalArgumentException("Password credentials cannot be persisted as a tracker session");
        }
    }
}
