package fr.vriege.anilib.framework.http;

import java.util.List;
import java.util.Map;

/** Lowest-level platform transport used beneath shared cookies, cache, and rate limiting. */
@FunctionalInterface
public interface HttpTransport {
    HttpResponse exchange(HttpRequest request, Map<String, List<String>> headers);
}
