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
import java.util.Map;

final class MiwayomiRuntimePatcher {
    private static final int CLASS_MAGIC = 0xCAFEBABE;
    private static final String QUICK_JS_CLASS = "/app/cash/quickjs/QuickJs.class";
    private static final String AUTO_CLOSEABLE = "java/lang/AutoCloseable";
    private static final String CLOSEABLE = "java/io/Closeable";

    private MiwayomiRuntimePatcher() {
    }

    static void apply(Path runtimeJar) {
        try (FileSystem archive = FileSystems.newFileSystem(runtimeJar, Map.of())) {
            Path quickJs = archive.getPath(QUICK_JS_CLASS);
            if (!Files.isRegularFile(quickJs)) {
                throw new IllegalStateException("Miwayomi runtime does not contain its QuickJs compatibility class");
            }
            Files.write(quickJs, replaceInterface(Files.readAllBytes(quickJs)));
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
