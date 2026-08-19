package fr.vriege.anilib.feature.tracker;

import fr.vriege.anilib.foundation.validation.Preconditions;

import java.util.Objects;

public record TrackerCredentials(TrackerAuthentication authentication, String identity, String secret) {
    public TrackerCredentials {
        Objects.requireNonNull(authentication, "authentication must not be null");
        identity = Objects.requireNonNull(identity, "identity must not be null").strip();
        Preconditions.requireNonBlank(secret, "secret");
        if (authentication == TrackerAuthentication.NONE) {
            throw new IllegalArgumentException("NONE does not accept credentials");
        }
        if (authentication == TrackerAuthentication.USERNAME_PASSWORD && identity.isBlank()) {
            throw new IllegalArgumentException("username must not be blank");
        }
    }

    public static TrackerCredentials password(String username, String password) {
        return new TrackerCredentials(TrackerAuthentication.USERNAME_PASSWORD, username, password);
    }

    public static TrackerCredentials token(String token) {
        return new TrackerCredentials(TrackerAuthentication.TOKEN, "", token);
    }

    public static TrackerCredentials authorizationCode(String code) {
        return new TrackerCredentials(TrackerAuthentication.OAUTH, "", code);
    }

    public static TrackerCredentials oauthResult(String result) {
        return new TrackerCredentials(TrackerAuthentication.OAUTH, "", result);
    }
}
