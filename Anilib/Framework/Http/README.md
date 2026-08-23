# HTTP framework

The HTTP framework separates shared request policy from the final platform
exchange. `Api` owns immutable requests and responses plus narrow contracts for
cookies, caching, rate limiting, and transport. `Runtime` combines those
contracts without depending on a platform SDK.

The Network Bundle publishes four explicit capabilities:

- `NetworkCapabilities.HTTP_CLIENT` for bounded synchronous exchanges;
- `NetworkCapabilities.COOKIES` for origin-aware cookie state;
- `NetworkCapabilities.RATE_LIMITER` for per-origin request intervals;
- `NetworkCapabilities.RESPONSE_CACHE` for explicit response retention.

Requests bypass the cache unless they opt into `preferCache` or `refresh` with
a positive lifetime. Only GET responses with successful status codes are
retained. Cache keys include the method, normalized URI, request headers, and
applied cookies; file names are SHA-256 digests and writes use atomic moves.

Desktop selects `JdkHttpTransport`, backed by the Java 21 HTTP client and HTTP/2.
The policy engine enforces a 16 MiB response safety limit. A
redirect response is returned to the caller instead of followed automatically,
which lets a permission-scoped source client authorize every origin hop. A future
platform can supply another `HttpTransport` without changing a source or
duplicating cookies, cache, or rate-limit behavior.
