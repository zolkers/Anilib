package fr.vriege.anilib.framework.http;

import java.util.List;
import java.util.Map;

@FunctionalInterface
public interface HttpTransport {
    HttpResponse exchange(HttpRequest request, Map<String, List<String>> headers);
}
