package fr.vriege.anilib.platform.desktopextensionhost.compat.aniyomi.network.interceptor;

import java.util.concurrent.TimeUnit;
import okhttp3.OkHttpClient;

public final class RateLimitInterceptorKt {
    private RateLimitInterceptorKt() {
    }

    public static OkHttpClient.Builder rateLimit(OkHttpClient.Builder builder, int permits,
                                                  long period, TimeUnit unit) {
        return builder.addInterceptor(new RateLimitInterceptor(permits, period, unit));
    }

    public static OkHttpClient.Builder rateLimit$default(OkHttpClient.Builder builder, int permits,
                                                          long period, TimeUnit unit, int mask, Object marker) {
        long actualPeriod = (mask & 2) == 0 ? period : 1L;
        TimeUnit actualUnit = (mask & 4) == 0 ? unit : TimeUnit.SECONDS;
        return rateLimit(builder, permits, actualPeriod, actualUnit);
    }
}
