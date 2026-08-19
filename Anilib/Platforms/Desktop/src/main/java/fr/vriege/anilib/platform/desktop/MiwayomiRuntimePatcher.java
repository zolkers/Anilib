package fr.vriege.anilib.platform.desktop;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Map;

final class MiwayomiRuntimePatcher {
    private static final int CLASS_MAGIC = 0xCAFEBABE;
    private static final String QUICK_JS_CLASS = "/app/cash/quickjs/QuickJs.class";
    private static final String AUTO_CLOSEABLE = "java/lang/AutoCloseable";
    private static final String CLOSEABLE = "java/io/Closeable";
    private static final String APP_INFO_CLASS = "/eu/kanade/tachiyomi/AppInfo.class";
    private static final byte[] APP_INFO_BYTES = Base64.getDecoder().decode(
            "yv66vgAAADQALwoAAgADBwAEDAAFAAYBABBqYXZhL2xhbmcvT2JqZWN0AQAGPGluaXQ+AQADKClW"
                    + "CAAIAQAOQW5pbGliIERlc2t0b3AHAAoBABBqYXZhL2xhbmcvU3RyaW5nCAAMAQAKaW1hZ2UvanBl"
                    + "ZwgADgEACWltYWdlL3BuZwgAEAEACWltYWdlL2dpZggAEgEACmltYWdlL3dlYnAIABQBAAppbWFn"
                    + "ZS9hdmlmCgAWABcHABgMABkAGgEAEGphdmEvdXRpbC9BcnJheXMBAAZhc0xpc3QBACUoW0xqYXZh"
                    + "L2xhbmcvT2JqZWN0OylMamF2YS91dGlsL0xpc3Q7BwAcAQAbZXUva2FuYWRlL3RhY2hpeW9taS9B"
                    + "cHBJbmZvCgAbAAMJABsAHwwAIAAhAQAISU5TVEFOQ0UBAB1MZXUva2FuYWRlL3RhY2hpeW9taS9B"
                    + "cHBJbmZvOwEABENvZGUBAA9MaW5lTnVtYmVyVGFibGUBAA5nZXRWZXJzaW9uQ29kZQEAAygpSQEA"
                    + "DmdldFZlcnNpb25OYW1lAQAUKClMamF2YS9sYW5nL1N0cmluZzsBABpnZXRTdXBwb3J0ZWRJbWFn"
                    + "ZU1pbWVUeXBlcwEAEigpTGphdmEvdXRpbC9MaXN0OwEACVNpZ25hdHVyZQEAJigpTGphdmEvdXRp"
                    + "bC9MaXN0PExqYXZhL2xhbmcvU3RyaW5nOz47AQAIPGNsaW5pdD4BAApTb3VyY2VGaWxlAQAMQXBw"
                    + "SW5mby5qYXZhADEAGwACAAAAAQAZACAAIQAAAAUAAgAFAAYAAQAiAAAAIQABAAEAAAAFKrcAAbEA"
                    + "AAABACMAAAAKAAIAAAAJAAQACgABACQAJQABACIAAAAaAAEAAQAAAAIErAAAAAEAIwAAAAYAAQAA"
                    + "AA0AAQAmACcAAQAiAAAAGwABAAEAAAADEgewAAAAAQAjAAAABgABAAAAEQABACgAKQACACIAAAA5"
                    + "AAQAAQAAACEIvQAJWQMSC1NZBBINU1kFEg9TWQYSEVNZBxITU7gAFbAAAAABACMAAAAGAAEAAAAV"
                    + "ACoAAAACACsACAAsAAYAAQAiAAAAIwACAAAAAAALuwAbWbcAHbMAHrEAAAABACMAAAAGAAEAAAAH"
                    + "AAEALQAAAAIALg==");

    private MiwayomiRuntimePatcher() {
    }

    static void apply(Path runtimeJar) {
        try (FileSystem archive = FileSystems.newFileSystem(runtimeJar, Map.of())) {
            Path quickJs = archive.getPath(QUICK_JS_CLASS);
            if (!Files.isRegularFile(quickJs)) {
                throw new IllegalStateException("Miwayomi runtime does not contain its QuickJs compatibility class");
            }
            Files.write(quickJs, replaceInterface(Files.readAllBytes(quickJs)));
            Path appInfo = archive.getPath(APP_INFO_CLASS);
            Files.createDirectories(appInfo.getParent());
            Files.write(appInfo, APP_INFO_BYTES);
        } catch (IOException exception) {
            throw new UncheckedIOException("Unable to apply the Miwayomi compatibility patch", exception);
        }
    }

    private static byte[] replaceInterface(byte[] classFile) throws IOException {
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(classFile));
                ByteArrayOutputStream bytes = new ByteArrayOutputStream(classFile.length);
                DataOutputStream output = new DataOutputStream(bytes)) {
            int magic = input.readInt();
            if (magic != CLASS_MAGIC) {
                throw new IllegalStateException("Miwayomi QuickJs entry is not a JVM class");
            }
            output.writeInt(magic);
            output.writeShort(input.readUnsignedShort());
            output.writeShort(input.readUnsignedShort());
            int constantPoolSize = input.readUnsignedShort();
            output.writeShort(constantPoolSize);
            int replacements = 0;
            for (int index = 1; index < constantPoolSize; index++) {
                int tag = input.readUnsignedByte();
                output.writeByte(tag);
                switch (tag) {
                    case 1 -> replacements += copyUtf8(input, output);
                    case 3, 4 -> copy(input, output, 4);
                    case 5, 6 -> {
                        copy(input, output, 8);
                        index++;
                    }
                    case 7, 8, 16, 19, 20 -> copy(input, output, 2);
                    case 9, 10, 11, 12, 17, 18 -> copy(input, output, 4);
                    case 15 -> copy(input, output, 3);
                    default -> throw new IllegalStateException("Unsupported JVM constant-pool tag " + tag);
                }
            }
            input.transferTo(output);
            if (replacements != 1) {
                throw new IllegalStateException("Miwayomi QuickJs compatibility signature was not found exactly once");
            }
            return bytes.toByteArray();
        }
    }

    private static int copyUtf8(DataInputStream input, DataOutputStream output) throws IOException {
        int length = input.readUnsignedShort();
        byte[] value = input.readNBytes(length);
        String text = new String(value, java.nio.charset.StandardCharsets.UTF_8);
        byte[] replacement = (text.equals(AUTO_CLOSEABLE) ? CLOSEABLE : text)
                .getBytes(java.nio.charset.StandardCharsets.UTF_8);
        output.writeShort(replacement.length);
        output.write(replacement);
        return text.equals(AUTO_CLOSEABLE) ? 1 : 0;
    }

    private static void copy(DataInputStream input, DataOutputStream output, int length) throws IOException {
        output.write(input.readNBytes(length));
    }
}
