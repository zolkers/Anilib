package fr.vriege.anilib.framework.http;

import java.net.URI;
import java.time.Duration;

/** Coordinates minimum request intervals independently for each origin. */
@FunctionalInterface
public interface HttpRateLimiter {
    void acquire(URI uri, Duration minimumInterval);
}
