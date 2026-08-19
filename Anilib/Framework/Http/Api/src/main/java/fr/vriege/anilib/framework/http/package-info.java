/**
 * Platform-neutral HTTP values, policies, and execution ports.
 *
 * <p>The API separates two execution levels:</p>
 *
 * <ul>
 *   <li>{@link fr.vriege.anilib.framework.http.AnilibHttpClient} is the
 *       application-facing client that applies shared cookies, caching, rate
 *       limiting, and default headers;</li>
 *   <li>{@link fr.vriege.anilib.framework.http.HttpTransport} performs one raw
 *       platform exchange using the effective headers supplied by that client.</li>
 * </ul>
 *
 * <p>{@link fr.vriege.anilib.framework.http.HttpRequest} and
 * {@link fr.vriege.anilib.framework.http.HttpResponse} defensively own mutable
 * byte arrays and collections. Requests validate schemes, hosts, headers,
 * timeouts, method/body combinations, and cache eligibility before reaching a
 * transport. Redirect responses remain visible so authorization code can check
 * every network hop independently.</p>
 *
 * <p>Policy storage is expressed through narrow ports:
 * {@link fr.vriege.anilib.framework.http.HttpCookieJar},
 * {@link fr.vriege.anilib.framework.http.HttpResponseCache}, and
 * {@link fr.vriege.anilib.framework.http.HttpRateLimiter}. This keeps shared
 * behavior independent of desktop or Android networking types.</p>
 */
package fr.vriege.anilib.framework.http;
