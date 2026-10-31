package fr.vriege.anilib.platform.desktopextensionhost.compat.android.net;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

public final class Uri {
    private final String value;

    private Uri(String value) {
        this.value = Objects.requireNonNull(value, "value");
    }

    public static Uri parse(String value) {
        return new Uri(value);
    }

    public static String encode(String value) {
        return encode(value, null);
    }

    public static String encode(String value, String allow) {
        if (value == null) return null;
        StringBuilder result = new StringBuilder();
        for (byte item : value.getBytes(StandardCharsets.UTF_8)) {
            int character = item & 255;
            if (character >= 'a' && character <= 'z'
                    || character >= 'A' && character <= 'Z'
                    || character >= '0' && character <= '9'
                    || "_-!.~'()*".indexOf(character) >= 0
                    || character < 128 && allow != null && allow.indexOf(character) >= 0) {
                result.append((char) character);
            } else {
                result.append('%')
                        .append("0123456789ABCDEF".charAt(character >> 4))
                        .append("0123456789ABCDEF".charAt(character & 15));
            }
        }
        return result.toString();
    }

    public static String decode(String value) {
        if (value == null) return null;
        StringBuilder result = new StringBuilder();
        for (int cursor = 0; cursor < value.length(); ) {
            if (value.charAt(cursor) != '%') {
                result.append(value.charAt(cursor++));
                continue;
            }
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            while (cursor < value.length() && value.charAt(cursor) == '%') {
                int high =
                        cursor + 1 < value.length()
                                ? Character.digit(value.charAt(cursor + 1), 16)
                                : -1;
                int low =
                        cursor + 2 < value.length()
                                ? Character.digit(value.charAt(cursor + 2), 16)
                                : -1;
                if (high < 0 || low < 0) {
                    bytes.writeBytes("\uFFFD".getBytes(StandardCharsets.UTF_8));
                    cursor = Math.min(value.length(), cursor + 3);
                } else {
                    bytes.write(high * 16 + low);
                    cursor += 3;
                }
            }
            result.append(bytes.toString(StandardCharsets.UTF_8));
        }
        return result.toString();
    }

    public static Uri fromFile(File file) {
        return new Uri(Objects.requireNonNull(file, "file").toURI().toString());
    }

    public String getScheme() {
        return URI.create(value).getScheme();
    }

    public String getHost() {
        return URI.create(value).getHost();
    }

    public String getPath() {
        return URI.create(value).getPath();
    }

    @Override
    public String toString() {
        return value;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Uri uri && value.equals(uri.value);
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }
}
