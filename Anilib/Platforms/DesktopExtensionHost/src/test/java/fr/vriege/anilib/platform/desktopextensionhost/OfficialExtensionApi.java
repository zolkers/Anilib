package fr.vriege.anilib.platform.desktopextensionhost;

import fr.vriege.anilib.platform.desktopextensionhost.extension.ExtensionAbiVerifier;
import fr.vriege.anilib.platform.desktopextensionhost.extension.ExtensionBytecodeRelocator;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.TreeSet;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.stream.Collectors;

/* Generates reproducible binary contracts from official Aniyomi classes.jar artifacts. */
public final class OfficialExtensionApi {
    private OfficialExtensionApi() {}

    public static void main(String[] args) throws Exception {
        if (args.length == 3 && args[0].equals("generate")) {
            generate(Path.of(args[1]), Path.of(args[2]));
            return;
        }
        if (args.length != 1)
            throw new IllegalArgumentException(
                    "Expected contract file, or generate <classes.jar> <contract>");
        var missing = inspect(Files.readAllLines(Path.of(args[0])));
        missing.forEach(System.out::println);
        if (!missing.isEmpty())
            throw new IllegalStateException("Official API: " + missing.size() + " missing symbols");
        System.out.println("Official API contract passed: " + args[0]);
    }

    static List<String> inspect(List<String> lines) throws IOException {
        List<String> missing = new ArrayList<>();
        var owners =
                lines.stream()
                        .filter(line -> !line.isBlank() && !line.startsWith("#"))
                        .collect(Collectors.groupingBy(line -> line.split("\\t")[1]));
        for (var group : owners.values()) missing.addAll(inspectGroup(group));
        return missing.stream().distinct().sorted().toList();
    }

    private static List<String> inspectGroup(List<String> lines) throws IOException {
        Path archive = Files.createTempFile("anilib-api-contract-", ".jar");
        try {
            ClassWriter writer = new ClassWriter(0);
            writer.visit(
                    Opcodes.V21,
                    Opcodes.ACC_PUBLIC,
                    "fixture/OfficialApiConsumer",
                    null,
                    "java/lang/Object",
                    null);
            MethodVisitor visitor =
                    writer.visitMethod(
                            Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                            "references",
                            "()V",
                            null,
                            null);
            visitor.visitCode();
            for (String line : lines) {
                if (line.isBlank() || line.startsWith("#")) continue;
                String[] entry = line.split("\\t");
                String owner = ExtensionBytecodeRelocator.relocatedApiType(entry[1]);
                switch (entry[0]) {
                    case "C" -> visitor.visitTypeInsn(Opcodes.CHECKCAST, owner);
                    case "M" ->
                            visitor.visitMethodInsn(
                                    (Integer.parseInt(entry[4]) & Opcodes.ACC_STATIC) != 0
                                            ? Opcodes.INVOKESTATIC
                                            : Opcodes.INVOKEVIRTUAL,
                                    owner,
                                    entry[2],
                                    ExtensionBytecodeRelocator.relocatedApiDescriptor(entry[3]),
                                    false);
                    case "F" ->
                            visitor.visitFieldInsn(
                                    (Integer.parseInt(entry[4]) & Opcodes.ACC_STATIC) != 0
                                            ? Opcodes.GETSTATIC
                                            : Opcodes.GETFIELD,
                                    owner,
                                    entry[2],
                                    ExtensionBytecodeRelocator.relocatedApiDescriptor(entry[3]));
                    default ->
                            throw new IllegalArgumentException(
                                    "Invalid API contract record: " + line);
                }
            }
            visitor.visitInsn(Opcodes.RETURN);
            visitor.visitMaxs(0, 0);
            visitor.visitEnd();
            writer.visitEnd();
            try (JarOutputStream jar = new JarOutputStream(Files.newOutputStream(archive))) {
                jar.putNextEntry(new JarEntry("fixture/OfficialApiConsumer.class"));
                jar.write(writer.toByteArray());
                jar.closeEntry();
            }
            return new ExtensionAbiVerifier().inspect(archive).missingSymbols();
        } finally {
            Files.deleteIfExists(archive);
        }
    }

    private static void generate(Path archive, Path output) throws Exception {
        TreeSet<String> entries = new TreeSet<>();
        try (JarFile jar = new JarFile(archive.toFile())) {
            for (JarEntry entry :
                    jar.stream().filter(e -> e.getName().endsWith(".class")).toList()) {
                try (var input = jar.getInputStream(entry)) {
                    new ClassReader(input)
                            .accept(
                                    new ClassVisitor(Opcodes.ASM9) {
                                        private String owner;
                                        private boolean visible;
                                        private final List<String> members = new ArrayList<>();

                                        @Override
                                        public void visit(
                                                int version,
                                                int access,
                                                String name,
                                                String signature,
                                                String parent,
                                                String[] interfaces) {
                                            owner = name;
                                            visible = (access & Opcodes.ACC_PUBLIC) != 0;
                                            members.add("C\t" + owner);
                                        }

                                        @Override
                                        public void visitOuterClass(
                                                String owner, String name, String descriptor) {
                                            visible = false;
                                        }

                                        @Override
                                        public void visitEnd() {
                                            if (visible) entries.addAll(members);
                                        }

                                        @Override
                                        public MethodVisitor visitMethod(
                                                int access,
                                                String name,
                                                String descriptor,
                                                String signature,
                                                String[] exceptions) {
                                            if ((access
                                                            & (Opcodes.ACC_PUBLIC
                                                                    | Opcodes.ACC_PROTECTED))
                                                    != 0)
                                                members.add(
                                                        "M\t"
                                                                + owner
                                                                + '\t'
                                                                + name
                                                                + '\t'
                                                                + descriptor
                                                                + '\t'
                                                                + access);
                                            return null;
                                        }

                                        @Override
                                        public FieldVisitor visitField(
                                                int access,
                                                String name,
                                                String descriptor,
                                                String signature,
                                                Object value) {
                                            if ((access
                                                            & (Opcodes.ACC_PUBLIC
                                                                    | Opcodes.ACC_PROTECTED))
                                                    != 0)
                                                members.add(
                                                        "F\t"
                                                                + owner
                                                                + '\t'
                                                                + name
                                                                + '\t'
                                                                + descriptor
                                                                + '\t'
                                                                + access);
                                            return null;
                                        }
                                    },
                                    ClassReader.SKIP_CODE
                                            | ClassReader.SKIP_DEBUG
                                            | ClassReader.SKIP_FRAMES);
                }
            }
        }
        List<String> lines = new ArrayList<>();
        lines.add("# Aniyomi official binary API; generated, do not hand-edit");
        lines.add(
                "# classes.jar SHA-256: "
                        + HexFormat.of()
                                .formatHex(
                                        MessageDigest.getInstance("SHA-256")
                                                .digest(Files.readAllBytes(archive))));
        lines.addAll(entries);
        Files.createDirectories(output.toAbsolutePath().getParent());
        Files.write(output, lines);
        System.out.println("Generated " + entries.size() + " official symbols: " + output);
    }
}
