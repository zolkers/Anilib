package fr.vriege.anilib.platform.desktopextensionhost;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.nio.file.Files;
import java.nio.file.Path;

/* Build-time JVM bridges for API versions which overload a method by return type. */
public final class CompatibilityBridges {
    private CompatibilityBridges() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 1)
            throw new IllegalArgumentException("Expected compiled class directory");
        Path directory = Path.of(args[0]).toAbsolutePath().normalize();
        try (var files = Files.walk(directory)) {
            for (Path file :
                    files.filter(path -> path.getFileName().toString().endsWith(".class"))
                            .toList()) {
                byte[] original = Files.readAllBytes(file);
                ClassWriter writer = new ClassWriter(0);
                boolean[] modified = {false};
                new ClassReader(original)
                        .accept(
                                new ClassVisitor(Opcodes.ASM9, writer) {
                                    @Override
                                    public MethodVisitor visitMethod(
                                            int access,
                                            String name,
                                            String descriptor,
                                            String signature,
                                            String[] exceptions) {
                                        if (name.startsWith("abi$")) {
                                            name = name.substring(4).replace('_', '-');
                                            modified[0] = true;
                                        }
                                        MethodVisitor target =
                                                super.visitMethod(
                                                        access,
                                                        name,
                                                        descriptor,
                                                        signature,
                                                        exceptions);
                                        return new MethodVisitor(Opcodes.ASM9, target) {
                                            @Override
                                            public void visitMethodInsn(
                                                    int opcode,
                                                    String owner,
                                                    String called,
                                                    String descriptor,
                                                    boolean contract) {
                                                if (called.startsWith("abi$")) {
                                                    called = called.substring(4).replace('_', '-');
                                                    modified[0] = true;
                                                }
                                                super.visitMethodInsn(
                                                        opcode,
                                                        owner,
                                                        called,
                                                        descriptor,
                                                        contract);
                                            }
                                        };
                                    }
                                },
                                0);
                if (modified[0]) Files.write(file, writer.toByteArray());
            }
        }
    }
}
