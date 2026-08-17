package fr.vriege.anilib.framework.http;

import java.net.URI;
import java.util.List;
import java.util.Map;

/** Cookie persistence boundary using standard HTTP header shapes. */
public interface HttpCookieJar {
    Map<String, List<String>> requestHeaders(URI uri);

    void store(URI uri, Map<String, List<String>> responseHeaders);

    void clear();
}
