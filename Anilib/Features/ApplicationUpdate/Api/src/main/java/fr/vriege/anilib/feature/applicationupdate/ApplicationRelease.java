package fr.vriege.anilib.feature.applicationupdate;

import java.net.URI;
import java.util.Objects;

public record ApplicationRelease(ApplicationVersion version, URI releasePage) {
    public ApplicationRelease {
        Objects.requireNonNull(version, "version must not be null");
        releasePage = Objects.requireNonNull(releasePage, "releasePage must not be null").normalize();
        if (!"https".equalsIgnoreCase(releasePage.getScheme()) || releasePage.getHost() == null) {
            throw new IllegalArgumentException("releasePage must be an absolute HTTPS URI");
        }
    }
}
