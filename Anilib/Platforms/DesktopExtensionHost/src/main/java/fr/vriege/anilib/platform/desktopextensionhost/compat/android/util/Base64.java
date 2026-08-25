package fr.vriege.anilib.platform.desktopextensionhost.compat.android.util;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public final class Base64 {
    public static final int DEFAULT = 0;
    public static final int NO_PADDING = 1;
    public static final int NO_WRAP = 2;
    public static final int CRLF = 4;
    public static final int URL_SAFE = 8;
    public static final int NO_CLOSE = 16;

    private Base64() {
    }

    public static byte[] decode(String input, int flags) {
        return decoder(flags).decode(input.getBytes(StandardCharsets.ISO_8859_1));
    }

    public static byte[] decode(byte[] input, int flags) {
        return decoder(flags).decode(input);
    }

    public static byte[] decode(byte[] input, int offset, int length, int flags) {
        return decode(Arrays.copyOfRange(input, offset, Math.addExact(offset, length)), flags);
    }

    public static byte[] encode(byte[] input, int flags) {
        return encoder(flags).encode(input);
    }

    public static byte[] encode(byte[] input, int offset, int length, int flags) {
        return encode(Arrays.copyOfRange(input, offset, Math.addExact(offset, length)), flags);
    }

    public static String encodeToString(byte[] input, int flags) {
        return new String(encode(input, flags), StandardCharsets.ISO_8859_1);
    }

    public static String encodeToString(byte[] input, int offset, int length, int flags) {
        return new String(encode(input, offset, length, flags), StandardCharsets.ISO_8859_1);
    }

    private static java.util.Base64.Decoder decoder(int flags) {
        return (flags & URL_SAFE) != 0
                ? java.util.Base64.getUrlDecoder()
                : java.util.Base64.getMimeDecoder();
    }

    private static java.util.Base64.Encoder encoder(int flags) {
        java.util.Base64.Encoder encoder;
        if ((flags & URL_SAFE) != 0) {
            encoder = java.util.Base64.getUrlEncoder();
        } else if ((flags & NO_WRAP) != 0) {
            encoder = java.util.Base64.getEncoder();
        } else {
            byte[] separator = (flags & CRLF) != 0
                    ? new byte[]{'\r', '\n'}
                    : new byte[]{'\n'};
            encoder = java.util.Base64.getMimeEncoder(76, separator);
        }
        return (flags & NO_PADDING) != 0 ? encoder.withoutPadding() : encoder;
    }
}
