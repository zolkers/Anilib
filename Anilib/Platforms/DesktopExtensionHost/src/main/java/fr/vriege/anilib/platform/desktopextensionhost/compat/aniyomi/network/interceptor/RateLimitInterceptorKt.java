package fr.vriege.anilib.platform.desktopextensionhost.compat.aniyomi.network.interceptor;

import okhttp3.OkHttpClient;

import java.util.concurrent.TimeUnit;

public final class RateLimitInterceptorKt {
    private RateLimitInterceptorKt() {}

    public static OkHttpClient.Builder abi$rateLimit_SxA4cEA(
            OkHttpClient.Builder builder, int permits, long duration) {
        return rateLimit(
                builder,
                permits,
                duration >> 1,
                (duration & 1) == 0 ? TimeUnit.NANOSECONDS : TimeUnit.MILLISECONDS);
    }

    public static OkHttpClient.Builder abi$rateLimit_SxA4cEA$default(
            OkHttpClient.Builder builder, int permits, long duration, int mask, Object marker) {
        return abi$rateLimit_SxA4cEA(builder, permits, (mask & 2) == 0 ? duration : 2_000_000_000L);
    }

    public static OkHttpClient.Builder rateLimit(
            OkHttpClient.Builder builder, int permits, long period, TimeUnit unit) {
        return builder.addInterceptor(new RateLimitInterceptor(permits, period, unit));
    }

    public static OkHttpClient.Builder rateLimit$default(
            OkHttpClient.Builder builder,
            int permits,
            long period,
            TimeUnit unit,
            int mask,
            Object marker) {
        long actualPeriod = (mask & 2) == 0 ? period : 1L;
        TimeUnit actualUnit = (mask & 4) == 0 ? unit : TimeUnit.SECONDS;
        return rateLimit(builder, permits, actualPeriod, actualUnit);
    }
}
