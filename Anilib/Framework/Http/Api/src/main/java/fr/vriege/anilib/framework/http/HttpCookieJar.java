package fr.vriege.anilib.framework.http;

import java.net.URI;
import java.util.List;
import java.util.Map;

/**
 * Stores response cookies and supplies applicable headers for later requests.
 *
 * <p>Cookie selection is URI-sensitive: implementations are responsible for
 * domain, path, scheme, expiration, and other cookie policy. Header names and
 * values are represented as a multimap because a cookie implementation may
 * emit more than one header value.</p>
 */
public interface HttpCookieJar {
    /**
     * Returns cookie headers applicable to a request URI.
     *
     * @param uri the non-null request URI
     * @return a non-null immutable header snapshot; empty when no cookie applies
     * @throws NullPointerException if {@code uri} is {@code null}
     * @throws HttpException if stored cookies cannot be read
     */
    Map<String, List<String>> requestHeaders(URI uri);

    /**
     * Accepts cookie-bearing response headers for a request URI.
     *
     * @param uri             the non-null request URI
     * @param responseHeaders the non-null response header multimap
     * @throws NullPointerException if either argument is {@code null}
     * @throws HttpException if accepted cookies cannot be stored
     */
    void store(URI uri, Map<String, List<String>> responseHeaders);

    /**
     * Removes all cookies known to this jar.
     *
     * @throws HttpException if the cookie store cannot be cleared
     */
    void clear();
}
