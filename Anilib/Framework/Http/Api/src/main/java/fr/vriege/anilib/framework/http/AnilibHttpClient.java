package fr.vriege.anilib.framework.http;

/**
 * Executes validated HTTP requests through the product's shared network
 * policy.
 *
 * <p>The client is the application-facing HTTP boundary. Implementations may
 * apply cookies, caching, rate limiting, default headers, proxy policy, and a
 * platform-selected {@link HttpTransport} before returning an immutable
 * {@link HttpResponse}.</p>
 *
 * @see HttpRequest
 * @see HttpResponse
 * @see HttpTransport
 */
@FunctionalInterface
public interface AnilibHttpClient {
    /**
     * Executes a request and returns its response.
     *
     * <p>A normal return represents an HTTP response regardless of status code;
     * protocol status such as {@code 404} or {@code 500} is available through
     * {@link HttpResponse#statusCode()}. Transport and policy failures are
     * reported as exceptions.</p>
     *
     * @param request the non-null validated request
     * @return the immutable network or cached response
     * @throws NullPointerException if {@code request} is {@code null}
     * @throws HttpException if the request cannot be executed
     */
    HttpResponse execute(HttpRequest request);
}
