package fr.vriege.anilib.framework.http;

import fr.vriege.anilib.foundation.validation.Preconditions;

import java.time.Duration;

public record HttpCachePolicy(Mode mode, Duration timeToLive) {
    private static final HttpCachePolicy BYPASS = new HttpCachePolicy(Mode.BYPASS, Duration.ZERO);

    public HttpCachePolicy {
        Preconditions.requireNonNull(mode, "mode");
        Preconditions.requireNonNull(timeToLive, "timeToLive");
        if (mode == Mode.BYPASS && !timeToLive.isZero()) {
            throw new IllegalArgumentException("A bypass policy cannot retain a response");
        }
        if (mode != Mode.BYPASS && (timeToLive.isZero() || timeToLive.isNegative())) {
            throw new IllegalArgumentException("A caching policy requires a positive time to live");
        }
    }

    public static HttpCachePolicy bypass() {
        return BYPASS;
    }

    public static HttpCachePolicy preferCache(Duration timeToLive) {
        return new HttpCachePolicy(Mode.PREFER_CACHE, timeToLive);
    }

    public static HttpCachePolicy refresh(Duration timeToLive) {
        return new HttpCachePolicy(Mode.REFRESH, timeToLive);
    }

    public boolean reads() {
        return mode == Mode.PREFER_CACHE;
    }

    public boolean writes() {
        return mode != Mode.BYPASS;
    }

    public enum Mode {
        BYPASS,
        PREFER_CACHE,
        REFRESH
    }
}
