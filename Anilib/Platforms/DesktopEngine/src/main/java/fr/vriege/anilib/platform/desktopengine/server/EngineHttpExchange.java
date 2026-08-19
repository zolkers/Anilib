package fr.vriege.anilib.platform.desktopengine.server;

import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

final class EngineHttpExchange {
    private static final String JSON_CONTENT_TYPE = "application/json; charset=utf-8";

    private EngineHttpExchange() {
    }

    static boolean requireGet(HttpExchange exchange) {
        if ("GET".equals(exchange.getRequestMethod())) {
            return true;
        }
        exchange.getResponseHeaders().set("Allow", "GET");
        json(exchange, 405, "{\"error\":\"method_not_allowed\"}");
        return false;
    }

    static void json(HttpExchange exchange, int status, String document) {
        byte[] body = document.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", JSON_CONTENT_TYPE);
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        try {
            exchange.sendResponseHeaders(status, body.length);
            try (OutputStream output = exchange.getResponseBody()) {
                output.write(body);
            }
        } catch (IOException exception) {
            throw new UncheckedIOException("Unable to write desktop engine response", exception);
        } finally {
            exchange.close();
        }
    }
}
