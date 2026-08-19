package fr.vriege.anilib.platform.desktopextensionhost.compat.quickjs;

import java.io.Closeable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/*
 * Bounded compatibility surface for extensions that use QuickJS to read simple
 * JavaScript data files.
 *
 * <p>The desktop host deliberately does not expose a general-purpose script
 * engine. It supports the global array declarations and deterministic JSON
 * projection used by current Aniyomi extensions, and fails closed for other
 * expressions.</p>
 */
public final class QuickJs implements Closeable {
    private static final int MAX_SCRIPT_CHARACTERS = 4 * 1024 * 1024;
    private static final int MAX_GLOBALS = 10_000;
    private static final int MAX_NESTING = 32;
    private static final String EPISODE_PROJECTION = "JSON.stringify(Object.keys(this)";

    private final Map<String, Object> globals = new LinkedHashMap<>();
    private boolean closed;

    private QuickJs() {
    }

    public static QuickJs create() {
        return new QuickJs();
    }

    public synchronized Object evaluate(String script) {
        ensureOpen();
        if (script == null) {
            throw new NullPointerException("script");
        }
        if (script.length() > MAX_SCRIPT_CHARACTERS) {
            throw new IllegalArgumentException("JavaScript input exceeds 4 MiB");
        }
        if (episodeProjection(script)) {
            return episodeGlobalsJson();
        }
        readGlobalArrays(script);
        return null;
    }

    public synchronized Object evaluate(String script, String fileName) {
        if (fileName == null) {
            throw new NullPointerException("fileName");
        }
        return evaluate(script);
    }

    @Override
    public synchronized void close() {
        closed = true;
        globals.clear();
    }

    private boolean episodeProjection(String script) {
        return script.contains(EPISODE_PROJECTION)
                && script.contains("/^eps[0-9]+$/")
                && script.contains("this[k]");
    }

    private void readGlobalArrays(String script) {
        int cursor = 0;
        while (cursor < script.length()) {
            Assignment assignment = nextAssignment(script, cursor);
            if (assignment == null) {
                return;
            }
            Parser parser = new Parser(script, assignment.valueStart());
            Object value = parser.array(0);
            globals.put(assignment.name(), value);
            if (globals.size() > MAX_GLOBALS) {
                throw new IllegalArgumentException("JavaScript declares too many globals");
            }
            cursor = parser.position();
        }
    }

    private Assignment nextAssignment(String script, int start) {
        int cursor = start;
        while (cursor < script.length()) {
            char current = script.charAt(cursor);
            if (current == '\'' || current == '"' || current == '`') {
                cursor = skipString(script, cursor, current);
                continue;
            }
            if (current == '/' && cursor + 1 < script.length()) {
                char next = script.charAt(cursor + 1);
                if (next == '/') {
                    cursor = skipLineComment(script, cursor + 2);
                    continue;
                }
                if (next == '*') {
                    cursor = skipBlockComment(script, cursor + 2);
                    continue;
                }
            }
            if (identifierStart(current)) {
                int nameStart = cursor++;
                while (cursor < script.length() && identifierPart(script.charAt(cursor))) {
                    cursor++;
                }
                String name = script.substring(nameStart, cursor);
                if (!episodeName(name)) {
                    continue;
                }
                int equals = whitespace(script, cursor);
                if (equals >= script.length() || script.charAt(equals) != '=') {
                    continue;
                }
                int value = whitespace(script, equals + 1);
                if (value < script.length() && script.charAt(value) == '[') {
                    return new Assignment(name, value);
                }
            } else {
                cursor++;
            }
        }
        return null;
    }

    private String episodeGlobalsJson() {
        List<Map.Entry<String, Object>> episodes = globals.entrySet().stream()
                .filter(entry -> episodeName(entry.getKey()))
                .sorted(Comparator.comparingInt(entry -> episodeNumber(entry.getKey())))
                .toList();
        StringBuilder result = new StringBuilder("[");
        for (int index = 0; index < episodes.size(); index++) {
            if (index > 0) {
                result.append(',');
            }
            appendJson(result, episodes.get(index).getValue());
        }
        return result.append(']').toString();
    }

    private void appendJson(StringBuilder target, Object value) {
        if (value == null) {
            target.append("null");
        } else if (value instanceof String text) {
            target.append('"');
            for (int index = 0; index < text.length(); index++) {
                char character = text.charAt(index);
                switch (character) {
                    case '"' -> target.append("\\\"");
                    case '\\' -> target.append("\\\\");
                    case '\b' -> target.append("\\b");
                    case '\f' -> target.append("\\f");
                    case '\n' -> target.append("\\n");
                    case '\r' -> target.append("\\r");
                    case '\t' -> target.append("\\t");
                    default -> {
                        if (character < 0x20) {
                            target.append("\\u%04x".formatted((int) character));
                        } else {
                            target.append(character);
                        }
                    }
                }
            }
            target.append('"');
        } else if (value instanceof List<?> list) {
            target.append('[');
            for (int index = 0; index < list.size(); index++) {
                if (index > 0) {
                    target.append(',');
                }
                appendJson(target, list.get(index));
            }
            target.append(']');
        } else if (value instanceof Number || value instanceof Boolean) {
            target.append(value);
        } else {
            throw new IllegalArgumentException("Unsupported JavaScript value");
        }
    }

    private void ensureOpen() {
        if (closed) {
            throw new IllegalStateException("QuickJs is closed");
        }
    }

    private static boolean episodeName(String value) {
        if (!value.startsWith("eps") || value.length() == 3) {
            return false;
        }
        for (int index = 3; index < value.length(); index++) {
            if (!Character.isDigit(value.charAt(index))) {
                return false;
            }
        }
        return true;
    }

    private static int episodeNumber(String value) {
        try {
            return Integer.parseInt(value.substring(3));
        } catch (NumberFormatException exception) {
            return Integer.MAX_VALUE;
        }
    }

    private static int whitespace(String script, int start) {
        int cursor = start;
        while (cursor < script.length() && Character.isWhitespace(script.charAt(cursor))) {
            cursor++;
        }
        return cursor;
    }

    private static int skipString(String script, int start, char delimiter) {
        int cursor = start + 1;
        while (cursor < script.length()) {
            char character = script.charAt(cursor++);
            if (character == '\\' && cursor < script.length()) {
                cursor++;
            } else if (character == delimiter) {
                return cursor;
            }
        }
        throw new IllegalArgumentException("Unterminated JavaScript string");
    }

    private static int skipLineComment(String script, int start) {
        int cursor = start;
        while (cursor < script.length() && script.charAt(cursor) != '\n') {
            cursor++;
        }
        return cursor;
    }

    private static int skipBlockComment(String script, int start) {
        int end = script.indexOf("*/", start);
        if (end < 0) {
            throw new IllegalArgumentException("Unterminated JavaScript comment");
        }
        return end + 2;
    }

    private static boolean identifierStart(char value) {
        return Character.isLetter(value) || value == '_' || value == '$';
    }

    private static boolean identifierPart(char value) {
        return Character.isLetterOrDigit(value) || value == '_' || value == '$';
    }

    private record Assignment(String name, int valueStart) {
    }

    private static final class Parser {
        private final String input;
        private int position;

        private Parser(String input, int position) {
            this.input = input;
            this.position = position;
        }

        private int position() {
            return position;
        }

        private List<Object> array(int depth) {
            if (depth >= MAX_NESTING) {
                throw new IllegalArgumentException("JavaScript array nesting is too deep");
            }
            expect('[');
            List<Object> values = new ArrayList<>();
            skipWhitespaceAndComments();
            while (!peek(']')) {
                values.add(value(depth + 1));
                skipWhitespaceAndComments();
                if (peek(',')) {
                    position++;
                    skipWhitespaceAndComments();
                    if (peek(']')) {
                        break;
                    }
                } else if (!peek(']')) {
                    throw new IllegalArgumentException("Expected ',' in JavaScript array");
                }
            }
            expect(']');
            return Collections.unmodifiableList(new ArrayList<>(values));
        }

        private Object value(int depth) {
            skipWhitespaceAndComments();
            if (position >= input.length()) {
                throw new IllegalArgumentException("Unexpected end of JavaScript array");
            }
            char current = input.charAt(position);
            if (current == '[') {
                return array(depth);
            }
            if (current == '\'' || current == '"') {
                return string(current);
            }
            int start = position;
            while (position < input.length()) {
                char character = input.charAt(position);
                if (character == ',' || character == ']'
                        || Character.isWhitespace(character)) {
                    break;
                }
                position++;
            }
            String literal = input.substring(start, position);
            return switch (literal) {
                case "null", "undefined" -> null;
                case "true" -> true;
                case "false" -> false;
                default -> number(literal);
            };
        }

        private Number number(String literal) {
            try {
                return literal.contains(".") || literal.contains("e") || literal.contains("E")
                        ? Double.parseDouble(literal)
                        : Long.parseLong(literal);
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException("Unsupported JavaScript array value", exception);
            }
        }

        private String string(char delimiter) {
            position++;
            StringBuilder result = new StringBuilder();
            while (position < input.length()) {
                char character = input.charAt(position++);
                if (character == delimiter) {
                    return result.toString();
                }
                if (character != '\\') {
                    result.append(character);
                    continue;
                }
                if (position >= input.length()) {
                    throw new IllegalArgumentException("Unterminated JavaScript escape");
                }
                char escaped = input.charAt(position++);
                switch (escaped) {
                    case '\'', '"', '\\', '/' -> result.append(escaped);
                    case 'b' -> result.append('\b');
                    case 'f' -> result.append('\f');
                    case 'n' -> result.append('\n');
                    case 'r' -> result.append('\r');
                    case 't' -> result.append('\t');
                    case 'u' -> result.append(codePoint(4));
                    case 'x' -> result.append(codePoint(2));
                    case '\n' -> {
                    }
                    case '\r' -> {
                        if (position < input.length() && input.charAt(position) == '\n') {
                            position++;
                        }
                    }
                    default -> result.append(escaped);
                }
            }
            throw new IllegalArgumentException("Unterminated JavaScript string");
        }

        private char codePoint(int digits) {
            if (position + digits > input.length()) {
                throw new IllegalArgumentException("Incomplete JavaScript hexadecimal escape");
            }
            String encoded = input.substring(position, position + digits);
            position += digits;
            try {
                return (char) Integer.parseInt(encoded, 16);
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException("Invalid JavaScript hexadecimal escape", exception);
            }
        }

        private void skipWhitespaceAndComments() {
            boolean advanced;
            do {
                advanced = false;
                while (position < input.length() && Character.isWhitespace(input.charAt(position))) {
                    position++;
                    advanced = true;
                }
                if (position + 1 < input.length() && input.charAt(position) == '/') {
                    char next = input.charAt(position + 1);
                    if (next == '/') {
                        position = skipLineComment(input, position + 2);
                        advanced = true;
                    } else if (next == '*') {
                        position = skipBlockComment(input, position + 2);
                        advanced = true;
                    }
                }
            } while (advanced);
        }

        private boolean peek(char expected) {
            return position < input.length() && input.charAt(position) == expected;
        }

        private void expect(char expected) {
            skipWhitespaceAndComments();
            if (!peek(expected)) {
                throw new IllegalArgumentException("Expected '" + expected + "' in JavaScript array");
            }
            position++;
        }
    }
}
