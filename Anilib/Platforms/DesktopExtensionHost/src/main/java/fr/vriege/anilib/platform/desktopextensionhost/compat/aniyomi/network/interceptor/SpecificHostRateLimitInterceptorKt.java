package fr.vriege.anilib.platform.desktopextensionhost.compat.aniyomi.network.interceptor;

import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;

import java.util.concurrent.TimeUnit;

public final class SpecificHostRateLimitInterceptorKt {
    private SpecificHostRateLimitInterceptorKt() {}

    public static OkHttpClient.Builder abi$rateLimitHost_Wn2Vu4Y(
            OkHttpClient.Builder builder, HttpUrl host, int permits, long duration) {
        return rateLimitHost(
                builder,
                host,
                permits,
                duration >> 1,
                (duration & 1) == 0 ? TimeUnit.NANOSECONDS : TimeUnit.MILLISECONDS);
    }

    public static OkHttpClient.Builder abi$rateLimitHost_Wn2Vu4Y(
            OkHttpClient.Builder builder, String host, int permits, long duration) {
        return abi$rateLimitHost_Wn2Vu4Y(builder, HttpUrl.get(host), permits, duration);
    }

    public static OkHttpClient.Builder abi$rateLimitHost_Wn2Vu4Y$default(
            OkHttpClient.Builder builder,
            HttpUrl host,
            int permits,
            long duration,
            int mask,
            Object marker) {
        return abi$rateLimitHost_Wn2Vu4Y(
                builder, host, permits, (mask & 4) == 0 ? duration : 2_000_000_000L);
    }

    public static OkHttpClient.Builder abi$rateLimitHost_Wn2Vu4Y$default(
            OkHttpClient.Builder builder,
            String host,
            int permits,
            long duration,
            int mask,
            Object marker) {
        return abi$rateLimitHost_Wn2Vu4Y(
                builder, host, permits, (mask & 4) == 0 ? duration : 2_000_000_000L);
    }

    public static OkHttpClient.Builder rateLimitHost(
            OkHttpClient.Builder builder, HttpUrl host, int permits, long period, TimeUnit unit) {
        return builder.addInterceptor(
                new SpecificHostRateLimitInterceptor(host, permits, period, unit));
    }

    public static OkHttpClient.Builder rateLimitHost$default(
            OkHttpClient.Builder builder,
            HttpUrl host,
            int permits,
            long period,
            TimeUnit unit,
            int mask,
            Object marker) {
        long actualPeriod = (mask & 4) == 0 ? period : 1L;
        TimeUnit actualUnit = (mask & 8) == 0 ? unit : TimeUnit.SECONDS;
        return rateLimitHost(builder, host, permits, actualPeriod, actualUnit);
    }
}
