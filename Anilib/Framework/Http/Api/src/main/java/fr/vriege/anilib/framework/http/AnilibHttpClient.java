package fr.vriege.anilib.framework.http;

@FunctionalInterface
public interface AnilibHttpClient {
    HttpResponse execute(HttpRequest request);
}
