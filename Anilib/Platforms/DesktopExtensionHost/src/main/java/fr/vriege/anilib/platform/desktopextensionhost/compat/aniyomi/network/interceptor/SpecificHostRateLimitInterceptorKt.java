package fr.vriege.anilib.platform.desktopextensionhost.compat.aniyomi.network.interceptor;

import java.util.concurrent.TimeUnit;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;

public final class SpecificHostRateLimitInterceptorKt {
    private SpecificHostRateLimitInterceptorKt() {
    }

    public static OkHttpClient.Builder rateLimitHost(OkHttpClient.Builder builder, HttpUrl host,
                                                       int permits, long period, TimeUnit unit) {
        return builder.addInterceptor(new SpecificHostRateLimitInterceptor(host, permits, period, unit));
    }

    public static OkHttpClient.Builder rateLimitHost$default(OkHttpClient.Builder builder, HttpUrl host,
                                                              int permits, long period, TimeUnit unit,
                                                              int mask, Object marker) {
        long actualPeriod = (mask & 4) == 0 ? period : 1L;
        TimeUnit actualUnit = (mask & 8) == 0 ? unit : TimeUnit.SECONDS;
        return rateLimitHost(builder, host, permits, actualPeriod, actualUnit);
    }
}
