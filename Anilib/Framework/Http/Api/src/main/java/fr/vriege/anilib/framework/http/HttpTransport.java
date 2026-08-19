package fr.vriege.anilib.framework.http;

import java.util.List;
import java.util.Map;

/**
 * Low-level, policy-neutral HTTP exchange port implemented at the platform
 * boundary.
 *
 * <p>The supplied request contains the validated URI, method, body, and timeout.
 * The separate header map is the effective set after shared policy has merged
 * request headers, cookies, and defaults. A transport must not apply response
 * caching, cookie persistence, rate limiting, or implicit redirect following;
 * those decisions belong to the shared client and authorization layers.</p>
 *
 * @see AnilibHttpClient
 */
@FunctionalInterface
public interface HttpTransport {
    /**
     * Performs one network exchange.
     *
     * <p>A normal return represents the received HTTP response for any status
     * code. The returned response must represent network provenance rather than
     * a cache hit.</p>
     *
     * @param request the non-null validated request
     * @param headers the non-null effective request-header multimap
     * @return the non-null immutable network response
     * @throws NullPointerException if either argument is {@code null}
     * @throws HttpException if the exchange cannot be completed
     *
     * @implSpec Implementations must not follow redirects automatically; redirect
     * responses must remain visible to callers for per-hop authorization.
     */
    HttpResponse exchange(HttpRequest request, Map<String, List<String>> headers);
}
