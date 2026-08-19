/**
 * Defines Anilib's platform-neutral HTTP request, response, policy, and port
 * contracts.
 *
 * <p>Shared code owns cache, cookie, and rate-limit policy while outer platform
 * modules supply low-level transports.</p>
 */
module fr.vriege.anilib.framework.http.api {
    requires transitive fr.vriege.anilib.foundation;

    exports fr.vriege.anilib.framework.http;
}
