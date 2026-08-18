package fr.vriege.anilib.feature.extensionrepository.runtime;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class JsonParser {
    private static final int MAX_DEPTH = 64;

    private final String input;
    private int position;

    private JsonParser(String input) {
        this.input = input;
    }

    static Object parse(String input) {
        JsonParser parser = new JsonParser(input);
        Object value = parser.value(0);
        parser.whitespace();
        if (!parser.end()) {
            throw parser.error("Unexpected trailing data");
        }
        return value;
    }

    private Object value(int depth) {
        if (depth > MAX_DEPTH) {
            throw error("JSON nesting exceeds " + MAX_DEPTH);
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
            return Collections.unmodifiableMap(new LinkedHashMap<>(values));
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
            return Collections.unmodifiableList(new ArrayList<>(values));
        }
        while (true) {
            values.add(value(depth));
            whitespace();
            if (take(']')) {
                return Collections.unmodifiableList(new ArrayList<>(values));
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
        int value = 0;
        for (int index = 0; index < 4; index++) {
            int digit = Character.digit(input.charAt(position++), 16);
            if (digit < 0) {
                throw error("Invalid Unicode escape");
            }
            value = value * 16 + digit;
        }
        return (char) value;
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
        boolean decimal = false;
        if (take('.')) {
            decimal = true;
            digits();
        }
        if (!end() && (current() == 'e' || current() == 'E')) {
            decimal = true;
            position++;
            if (!end() && (current() == '+' || current() == '-')) {
                position++;
            }
            digits();
        }
        if (start == position) {
            throw error("Expected JSON value");
        }
        String token = input.substring(start, position);
        try {
            return decimal ? new BigDecimal(token) : Long.valueOf(token);
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
        while (!end()) {
            char value = current();
            if (value != ' ' && value != '\n' && value != '\r' && value != '\t') {
                return;
            }
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
