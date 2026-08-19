package fr.vriege.anilib.platform.desktopextensionhost.server;

import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.net.URLDecoder;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Locale;

final class ExtensionHostHttpExchange {
    private static final String JSON_CONTENT_TYPE = "application/json; charset=utf-8";

    private ExtensionHostHttpExchange() {
    }

    static boolean requireGet(HttpExchange exchange) {
        if ("GET".equals(exchange.getRequestMethod())) {
            return true;
        }
        exchange.getResponseHeaders().set("Allow", "GET");
        json(exchange, 405, "{\"error\":\"method_not_allowed\"}");
        return false;
    }

    static boolean requirePost(HttpExchange exchange) {
        if ("POST".equals(exchange.getRequestMethod())) {
            return true;
        }
        exchange.getResponseHeaders().set("Allow", "POST");
        json(exchange, 405, "{\"error\":\"method_not_allowed\"}");
        return false;
    }

    static String body(HttpExchange exchange) {
        try (InputStream input = exchange.getRequestBody()) {
            byte[] bytes = input.readNBytes(65_537);
            if (bytes.length > 65_536) {
                throw new IllegalArgumentException("Request body is too large");
            }
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new UncheckedIOException("Unable to read desktop engine request", exception);
        }
    }

    static String stringField(String document, String field) {
        int cursor = skipWhitespace(document, 0);
        if (cursor >= document.length() || document.charAt(cursor) != '{') {
            throw new IllegalArgumentException("Request body must be a JSON object");
        }
        cursor++;
        while (true) {
            cursor = skipWhitespace(document, cursor);
            if (cursor < document.length() && document.charAt(cursor) == '}') {
                break;
            }
            ParsedString name = parseString(document, cursor);
            cursor = skipWhitespace(document, name.next());
            if (cursor >= document.length() || document.charAt(cursor) != ':') {
                throw new IllegalArgumentException("Invalid JSON object field");
            }
            cursor = skipWhitespace(document, cursor + 1);
            ParsedString value = parseString(document, cursor);
            if (field.equals(name.value())) {
                return value.value();
            }
            cursor = skipWhitespace(document, value.next());
            if (cursor < document.length() && document.charAt(cursor) == ',') {
                cursor++;
            } else if (cursor >= document.length() || document.charAt(cursor) != '}') {
                throw new IllegalArgumentException("Invalid JSON object separator");
            }
        }
        throw new IllegalArgumentException("Missing JSON field: " + field);
    }

    static String jsonString(String value) {
        StringBuilder result = new StringBuilder("\"");
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            switch (character) {
                case '"' -> result.append("\\\"");
                case '\\' -> result.append("\\\\");
                case '\b' -> result.append("\\b");
                case '\f' -> result.append("\\f");
                case '\n' -> result.append("\\n");
                case '\r' -> result.append("\\r");
                case '\t' -> result.append("\\t");
                default -> {
                    if (character < 0x20) {
                        result.append(String.format(Locale.ROOT, "\\u%04x", (int) character));
                    } else {
                        result.append(character);
                    }
                }
            }
        }
        return result.append('"').toString();
    }

    static Map<String, String> query(HttpExchange exchange) {
        String raw = exchange.getRequestURI().getRawQuery();
        if (raw == null || raw.isBlank()) {
            return Map.of();
        }
        Map<String, String> result = new LinkedHashMap<>();
        for (String field : raw.split("&")) {
            int separator = field.indexOf('=');
            String name = separator < 0 ? field : field.substring(0, separator);
            String value = separator < 0 ? "" : field.substring(separator + 1);
            result.put(decode(name), decode(value));
        }
        return Map.copyOf(result);
    }

    private static String decode(String value) {
        try {
            return URLDecoder.decode(value, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Invalid URL query encoding", exception);
        }
    }

    private static int skipWhitespace(String document, int cursor) {
        int result = cursor;
        while (result < document.length() && Character.isWhitespace(document.charAt(result))) {
            result++;
        }
        return result;
    }

    private static ParsedString parseString(String document, int cursor) {
        if (cursor >= document.length() || document.charAt(cursor) != '"') {
            throw new IllegalArgumentException("JSON value must be a string");
        }
        StringBuilder result = new StringBuilder();
        int index = cursor + 1;
        while (index < document.length()) {
            char character = document.charAt(index++);
            if (character == '"') {
                return new ParsedString(result.toString(), index);
            }
            if (character != '\\') {
                if (character < 0x20) {
                    throw new IllegalArgumentException("Invalid control character in JSON string");
                }
                result.append(character);
                continue;
            }
            if (index >= document.length()) {
                throw new IllegalArgumentException("Invalid JSON escape");
            }
            char escaped = document.charAt(index++);
            switch (escaped) {
                case '"', '\\', '/' -> result.append(escaped);
                case 'b' -> result.append('\b');
                case 'f' -> result.append('\f');
                case 'n' -> result.append('\n');
                case 'r' -> result.append('\r');
                case 't' -> result.append('\t');
                case 'u' -> {
                    if (index + 4 > document.length()) {
                        throw new IllegalArgumentException("Invalid JSON unicode escape");
                    }
                    try {
                        result.append((char) Integer.parseInt(document.substring(index, index + 4), 16));
                    } catch (NumberFormatException exception) {
                        throw new IllegalArgumentException("Invalid JSON unicode escape", exception);
                    }
                    index += 4;
                }
                default -> throw new IllegalArgumentException("Invalid JSON escape");
            }
        }
        throw new IllegalArgumentException("Unterminated JSON string");
    }

    static void json(HttpExchange exchange, int status, String document) {
        bytes(exchange, status, JSON_CONTENT_TYPE, document.getBytes(StandardCharsets.UTF_8));
    }

    static void bytes(HttpExchange exchange, int status, String contentType, byte[] body) {
        exchange.getResponseHeaders().set("Content-Type", contentType);
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

    private record ParsedString(String value, int next) {
    }
}
