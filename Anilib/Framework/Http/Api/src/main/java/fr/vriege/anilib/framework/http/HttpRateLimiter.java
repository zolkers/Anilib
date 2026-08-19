package fr.vriege.anilib.framework.http;

import java.net.URI;
import java.time.Duration;

/**
 * Admission control for spacing requests to the same network origin.
 *
 * <p>The shared HTTP client invokes the limiter immediately before transport
 * execution. Implementations may block the caller until the requested minimum
 * interval can be honored and should keep distinct origins independent.</p>
 */
@FunctionalInterface
public interface HttpRateLimiter {
    /**
     * Waits until a request to {@code uri}'s origin may begin.
     *
     * @param uri             the non-null request URI
     * @param minimumInterval the non-negative interval between admitted
     *                        requests to the same origin
     * @throws NullPointerException if either argument is {@code null}
     * @throws IllegalArgumentException if {@code minimumInterval} is negative
     * @throws HttpException if admission is interrupted or otherwise fails
     */
    void acquire(URI uri, Duration minimumInterval);
}
