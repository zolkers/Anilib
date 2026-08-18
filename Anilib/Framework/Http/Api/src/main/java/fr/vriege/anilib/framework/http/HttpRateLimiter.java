package fr.vriege.anilib.framework.http;

import java.net.URI;
import java.time.Duration;

@FunctionalInterface
public interface HttpRateLimiter {
    void acquire(URI uri, Duration minimumInterval);
}
