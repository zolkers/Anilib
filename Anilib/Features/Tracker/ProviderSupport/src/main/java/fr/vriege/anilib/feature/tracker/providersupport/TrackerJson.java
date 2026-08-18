package fr.vriege.anilib.feature.tracker.providersupport;

import fr.vriege.anilib.feature.tracker.TrackerException;
import fr.vriege.anilib.framework.http.AnilibHttpClient;
import fr.vriege.anilib.framework.http.HttpRequest;
import fr.vriege.anilib.framework.http.HttpResponse;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class TrackerJson {
    private static final int MAXIMUM_DEPTH = 64;
    private static final int MAXIMUM_RESPONSE_BYTES = 8 * 1024 * 1024;

    private TrackerJson() {
    }

    public static Object execute(AnilibHttpClient client, HttpRequest request) {
        HttpResponse response = Objects.requireNonNull(client, "client must not be null")
                .execute(Objects.requireNonNull(request, "request must not be null"));
        if (response.body().length > MAXIMUM_RESPONSE_BYTES) {
            throw new TrackerException("Tracker response exceeds the supported size");
        }
        Object document;
        try {
            document = parse(new String(response.body(), StandardCharsets.UTF_8));
        } catch (IllegalArgumentException exception) {
            throw new TrackerException("Tracker returned invalid JSON", exception);
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new TrackerException(errorMessage(document).orElse(
                    "Tracker request failed with HTTP " + response.statusCode()));
        }
        return document;
    }

    public static Object parse(String input) {
        Parser parser = new Parser(Objects.requireNonNull(input, "input must not be null"));
        Object value = parser.value(0);
        parser.whitespace();
        if (!parser.end()) {
            throw parser.error("Unexpected trailing data");
        }
        return value;
    }

    public static String encode(Object value) {
        StringBuilder output = new StringBuilder();
        write(output, value, 0);
        return output.toString();
    }

    public static Map<String, Object> object(Object value, String name) {
        if (!(value instanceof Map<?, ?> source)) {
            throw new TrackerException(name + " must be a JSON object");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, child) -> {
            if (!(key instanceof String string)) {
                throw new TrackerException(name + " contains a non-string key");
            }
            result.put(string, child);
        });
        return Collections.unmodifiableMap(result);
    }

    public static List<Object> array(Object value, String name) {
        if (!(value instanceof List<?> source)) {
            throw new TrackerException(name + " must be a JSON array");
        }
        return List.copyOf(source);
    }

    public static String string(Object value, String name) {
        if (!(value instanceof String result) || result.isBlank()) {
            throw new TrackerException(name + " must be a non-blank JSON string");
        }
        return result;
    }

    public static Optional<String> optionalString(Object value) {
        return value instanceof String string && !string.isBlank() ? Optional.of(string) : Optional.empty();
    }

    public static long longValue(Object value, String name) {
        if (!(value instanceof Number number)) {
            throw new TrackerException(name + " must be a JSON number");
        }
        try {
            return new BigDecimal(number.toString()).longValueExact();
        } catch (ArithmeticException exception) {
            throw new TrackerException(name + " must be an integer", exception);
        }
    }

    public static double doubleValue(Object value, String name) {
        if (!(value instanceof Number number) || !Double.isFinite(number.doubleValue())) {
            throw new TrackerException(name + " must be a finite JSON number");
        }
        return number.doubleValue();
    }

    public static boolean booleanValue(Object value, boolean fallback) {
        return value instanceof Boolean result ? result : fallback;
    }

    public static Map<String, Object> memberObject(Map<String, Object> value, String name) {
        return object(value.get(name), name);
    }

    public static List<Object> memberArray(Map<String, Object> value, String name) {
        return array(value.get(name), name);
    }

    public static String memberString(Map<String, Object> value, String name) {
        return string(value.get(name), name);
    }

    private static Optional<String> errorMessage(Object document) {
        if (!(document instanceof Map<?, ?>)) {
            return Optional.empty();
        }
        Map<String, Object> root = object(document, "error response");
        Optional<String> direct = optionalString(root.get("error_description"))
                .or(() -> optionalString(root.get("message")))
                .or(() -> optionalString(root.get("error")));
        if (direct.isPresent()) {
            return direct;
        }
        Object errors = root.get("errors");
        if (errors instanceof List<?> values && !values.isEmpty() && values.getFirst() instanceof Map<?, ?>) {
            return optionalString(object(values.getFirst(), "error").get("message"));
        }
        return Optional.empty();
    }

    private static void write(StringBuilder output, Object value, int depth) {
        if (depth > MAXIMUM_DEPTH) {
            throw new IllegalArgumentException("JSON nesting exceeds " + MAXIMUM_DEPTH);
        }
        switch (value) {
            case null -> output.append("null");
            case String string -> writeString(output, string);
            case Boolean bool -> output.append(bool);
            case Number number -> {
                boolean invalidDouble = number instanceof Double decimal && !Double.isFinite(decimal);
                boolean invalidFloat = number instanceof Float decimal && !Float.isFinite(decimal);
                if (invalidDouble || invalidFloat) {
                    throw new IllegalArgumentException("JSON numbers must be finite");
                }
                output.append(number);
            }
            case Map<?, ?> map -> {
                output.append('{');
                boolean first = true;
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    if (!(entry.getKey() instanceof String key)) {
                        throw new IllegalArgumentException("JSON object keys must be strings");
                    }
                    if (!first) {
                        output.append(',');
                    }
                    first = false;
                    writeString(output, key);
                    output.append(':');
                    write(output, entry.getValue(), depth + 1);
                }
                output.append('}');
            }
            case Iterable<?> iterable -> {
                output.append('[');
                boolean first = true;
                for (Object child : iterable) {
                    if (!first) {
                        output.append(',');
                    }
                    first = false;
                    write(output, child, depth + 1);
                }
                output.append(']');
            }
            default -> throw new IllegalArgumentException("Unsupported JSON value: " + value.getClass());
        }
    }

    private static void writeString(StringBuilder output, String value) {
        output.append('"');
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            switch (character) {
                case '"' -> output.append("\\\"");
                case '\\' -> output.append("\\\\");
                case '\b' -> output.append("\\b");
                case '\f' -> output.append("\\f");
                case '\n' -> output.append("\\n");
                case '\r' -> output.append("\\r");
                case '\t' -> output.append("\\t");
                default -> {
                    if (character < 0x20) {
                        output.append(String.format(java.util.Locale.ROOT, "\\u%04x", (int) character));
                    } else {
                        output.append(character);
                    }
                }
            }
        }
        output.append('"');
    }

    private static final class Parser {
        private final String input;
        private int position;

        private Parser(String input) {
            this.input = input;
        }

        private Object value(int depth) {
            if (depth > MAXIMUM_DEPTH) {
                throw error("JSON nesting exceeds " + MAXIMUM_DEPTH);
            }
            whitespace();
            if (end()) {
                throw error("Expected JSON value");
            }
            return switch (current()) {
                case '{' -> object(depth + 1);
                case '[' -> array(depth + 1);
                case '"' -> string();
                case 't' -> keyword("true", Boolean.TRUE);
                case 'f' -> keyword("false", Boolean.FALSE);
                case 'n' -> keyword("null", null);
                default -> number();
            };
        }

        private Map<String, Object> object(int depth) {
            expect('{');
            whitespace();
            Map<String, Object> values = new LinkedHashMap<>();
            if (take('}')) {
                return Map.copyOf(values);
            }
            while (true) {
                whitespace();
                if (end() || current() != '"') {
                    throw error("Expected object key");
                }
                String key = string();
                whitespace();
                expect(':');
                if (values.containsKey(key)) {
                    throw error("Duplicate object key: " + key);
                }
                values.put(key, value(depth));
                whitespace();
                if (take('}')) {
                    return Collections.unmodifiableMap(new LinkedHashMap<>(values));
                }
                expect(',');
            }
        }

        private List<Object> array(int depth) {
            expect('[');
            whitespace();
            List<Object> values = new ArrayList<>();
            if (take(']')) {
                return List.of();
            }
            while (true) {
                values.add(value(depth));
                whitespace();
                if (take(']')) {
                    return List.copyOf(values);
                }
                expect(',');
            }
        }

        private String string() {
            expect('"');
            StringBuilder result = new StringBuilder();
            while (!end()) {
                char value = input.charAt(position++);
                if (value == '"') {
                    return result.toString();
                }
                if (value == '\\') {
                    result.append(escape());
                } else if (value < 0x20) {
                    throw error("Control character in JSON string");
                } else {
                    result.append(value);
                }
            }
            throw error("Unterminated JSON string");
        }

        private char escape() {
            if (end()) {
                throw error("Unterminated JSON escape");
            }
            return switch (input.charAt(position++)) {
                case '"' -> '"';
                case '\\' -> '\\';
                case '/' -> '/';
                case 'b' -> '\b';
                case 'f' -> '\f';
                case 'n' -> '\n';
                case 'r' -> '\r';
                case 't' -> '\t';
                case 'u' -> unicodeEscape();
                default -> throw error("Invalid JSON escape");
            };
        }

        private char unicodeEscape() {
            if (position + 4 > input.length()) {
                throw error("Incomplete Unicode escape");
            }
            int result = 0;
            for (int index = 0; index < 4; index++) {
                int digit = Character.digit(input.charAt(position++), 16);
                if (digit < 0) {
                    throw error("Invalid Unicode escape");
                }
                result = result * 16 + digit;
            }
            return (char) result;
        }

        private Object number() {
            int start = position;
            take('-');
            if (take('0')) {
                if (!end() && Character.isDigit(current())) {
                    throw error("JSON number cannot contain a leading zero");
                }
            } else {
                digits();
            }
            if (take('.')) {
                digits();
            }
            if (!end() && (current() == 'e' || current() == 'E')) {
                position++;
                if (!end() && (current() == '+' || current() == '-')) {
                    position++;
                }
                digits();
            }
            if (start == position) {
                throw error("Expected JSON value");
            }
            try {
                return new BigDecimal(input.substring(start, position));
            } catch (NumberFormatException exception) {
                throw error("Invalid JSON number");
            }
        }

        private void digits() {
            int start = position;
            while (!end() && Character.isDigit(current())) {
                position++;
            }
            if (start == position) {
                throw error("Expected digit");
            }
        }

        private Object keyword(String keyword, Object value) {
            if (!input.startsWith(keyword, position)) {
                throw error("Invalid JSON keyword");
            }
            position += keyword.length();
            return value;
        }

        private void whitespace() {
            while (!end() && Character.isWhitespace(current())) {
                position++;
            }
        }

        private void expect(char expected) {
            if (!take(expected)) {
                throw error("Expected '" + expected + "'");
            }
        }

        private boolean take(char expected) {
            if (!end() && current() == expected) {
                position++;
                return true;
            }
            return false;
        }

        private char current() {
            return input.charAt(position);
        }

        private boolean end() {
            return position >= input.length();
        }

        private IllegalArgumentException error(String message) {
            return new IllegalArgumentException(message + " at JSON offset " + position);
        }
    }
}
