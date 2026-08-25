package fr.vriege.anilib.platform.desktopextensionhost.compat.aniyomi.network.interceptor;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
import okhttp3.HttpUrl;
import okhttp3.Interceptor;
import okhttp3.Response;

public final class SpecificHostRateLimitInterceptor implements Interceptor {
    private final String host;
    private final long intervalNanos;
    private long nextRequestNanos;

    public SpecificHostRateLimitInterceptor(HttpUrl url, int permits, long period, TimeUnit unit) {
        if (permits <= 0 || period < 0) {
            throw new IllegalArgumentException("Rate limit must have positive permits and a non-negative period");
        }
        host = url.host();
        intervalNanos = unit.toNanos(period) / permits;
    }

    @Override
    public Response intercept(Chain chain) throws IOException {
        if (chain.request().url().host().equalsIgnoreCase(host)) {
            awaitTurn();
        }
        return chain.proceed(chain.request());
    }

    private void awaitTurn() throws IOException {
        long wait;
        synchronized (this) {
            long now = System.nanoTime();
            wait = Math.max(0L, nextRequestNanos - now);
            nextRequestNanos = Math.max(now, nextRequestNanos) + intervalNanos;
        }
        if (wait > 0L) {
            LockSupport.parkNanos(wait);
            if (Thread.interrupted()) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted while applying source rate limit");
            }
        }
    }
}
