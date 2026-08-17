package fr.vriege.anilib.framework.http;

/** Synchronous transport boundary shared by source extensions and products. */
@FunctionalInterface
public interface AnilibHttpClient {
    HttpResponse execute(HttpRequest request);
}
