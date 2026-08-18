package fr.vriege.anilib.framework.http.runtime;

import fr.vriege.anilib.framework.http.HttpException;
import fr.vriege.anilib.framework.http.HttpRateLimiter;

import java.net.URI;
import java.time.Duration;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

public final class HostHttpRateLimiter implements HttpRateLimiter {
    private final Map<String, Long> nextSlots = new HashMap<>();

    public HostHttpRateLimiter() {
    }

    @Override
    public void acquire(URI uri, Duration minimumInterval) {
        Objects.requireNonNull(uri, "uri must not be null");
        Objects.requireNonNull(minimumInterval, "minimumInterval must not be null");
        if (minimumInterval.isNegative()) {
            throw new IllegalArgumentException("minimumInterval must not be negative");
        }
        if (minimumInterval.isZero()) {
            return;
        }

        long waitNanos;
        synchronized (nextSlots) {
            long now = System.nanoTime();
            String origin = origin(uri);
            long start = Math.max(now, nextSlots.getOrDefault(origin, now));
            waitNanos = Math.max(0L, start - now);
            nextSlots.put(origin, saturatingAdd(start, minimumInterval.toNanos()));
        }
        sleep(waitNanos);
    }

    private static String origin(URI uri) {
        String scheme = Objects.requireNonNull(uri.getScheme(), "uri scheme must not be null")
                .toLowerCase(Locale.ROOT);
        String host = Objects.requireNonNull(uri.getHost(), "uri host must not be null")
                .toLowerCase(Locale.ROOT);
        int port = uri.getPort();
        int effectivePort = port >= 0 ? port : (scheme.equals("https") ? 443 : 80);
        return scheme + "://" + host + ':' + effectivePort;
    }

    private static long saturatingAdd(long left, long right) {
        if (right > 0 && left > Long.MAX_VALUE - right) {
            return Long.MAX_VALUE;
        }
        return left + right;
    }

    private static void sleep(long nanos) {
        if (nanos <= 0) {
            return;
        }
        try {
            TimeUnit.NANOSECONDS.sleep(nanos);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new HttpException("Interrupted while waiting for the HTTP rate limit", exception);
        }
    }
}
