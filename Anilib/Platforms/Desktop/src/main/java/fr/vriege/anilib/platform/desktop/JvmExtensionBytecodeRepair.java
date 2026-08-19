package fr.vriege.anilib.platform.desktop;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;

final class JvmExtensionBytecodeRepair {
    private static final long MAX_EXTENSION_BYTES = 64L * 1024L * 1024L;
    private static final int ACC_PUBLIC = 1;
    private static final int ALOAD = 25;
    private static final int ASTORE = 58;
    private static final int DUP = 89;
    private static final int RETURN = 177;
    private static final int PUTSTATIC = 179;
    private static final int PUTFIELD = 181;
    private static final int INVOKESPECIAL = 183;
    private static final int NEW = 187;

    private JvmExtensionBytecodeRepair() {
    }

    static int repair(Path extensionJar, Path engineRuntime) {
        Path source = requireRegularFile(extensionJar, "extension JAR");
        Path runtime = requireRegularFile(engineRuntime, "engine runtime");
        try {
            if (Files.size(source) > MAX_EXTENSION_BYTES) {
                throw new IllegalArgumentException("Converted extension exceeds the repair size limit");
            }
            Path repaired = null;
            int changes;
            try (URLClassLoader loader = new URLClassLoader(
                    new URL[]{source.toUri().toURL(), runtime.toUri().toURL()},
                    ClassLoader.getPlatformClassLoader())) {
                Asm asm = new Asm(loader);
                Archive archive = read(source, asm);
                changes = asm.repair(archive.classes());
                if (changes == 0) {
                    return 0;
                }
                repaired = writeTemporary(source, archive, asm);
            }
            try {
                replace(repaired, source);
            } finally {
                Files.deleteIfExists(repaired);
            }
            return changes;
        } catch (IOException exception) {
            throw new UncheckedIOException("Unable to repair converted extension bytecode", exception);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(
                    "Desktop engine does not expose the expected bytecode repair API",
                    exception);
        }
    }

    private static Archive read(Path source, Asm asm) throws IOException, ReflectiveOperationException {
        Map<String, Object> classes = new LinkedHashMap<>();
        Map<String, byte[]> resources = new LinkedHashMap<>();
        long expanded = 0;
        try (JarFile archive = new JarFile(source.toFile())) {
            Enumeration<JarEntry> entries = archive.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (entry.isDirectory()) {
                    continue;
                }
                byte[] bytes;
                try (InputStream input = archive.getInputStream(entry)) {
                    bytes = input.readNBytes(Math.toIntExact(MAX_EXTENSION_BYTES + 1));
                }
                expanded = Math.addExact(expanded, bytes.length);
                if (bytes.length > MAX_EXTENSION_BYTES || expanded > MAX_EXTENSION_BYTES) {
                    throw new IllegalArgumentException("Converted extension expands beyond the repair size limit");
                }
                if (entry.getName().endsWith(".class")) {
                    Object node = asm.readClass(bytes);
                    classes.put(asm.className(node), node);
                } else {
                    resources.put(entry.getName(), bytes);
                }
            }
        }
        return new Archive(classes, resources);
    }

    private static Path writeTemporary(Path destination, Archive archive, Asm asm)
            throws IOException, ReflectiveOperationException {
        Path temporary = Files.createTempFile(destination.getParent(), ".anilib-repair-", ".jar");
        try {
            try (OutputStream file = Files.newOutputStream(temporary);
                    JarOutputStream output = new JarOutputStream(file)) {
                for (Object node : archive.classes().values()) {
                    writeEntry(output, asm.className(node) + ".class", asm.writeClass(node));
                }
                for (Map.Entry<String, byte[]> resource : archive.resources().entrySet()) {
                    writeEntry(output, resource.getKey(), resource.getValue());
                }
            }
            return temporary;
        } catch (IOException | ReflectiveOperationException | RuntimeException failure) {
            Files.deleteIfExists(temporary);
            throw failure;
        }
    }

    private static void replace(Path source, Path destination) throws IOException {
        try {
            Files.move(source, destination, StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(source, destination, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void writeEntry(JarOutputStream output, String name, byte[] bytes) throws IOException {
        JarEntry entry = new JarEntry(name);
        entry.setTime(0L);
        output.putNextEntry(entry);
        output.write(bytes);
        output.closeEntry();
    }

    private static Path requireRegularFile(Path path, String label) {
        Path normalized = path.toAbsolutePath().normalize();
        if (!Files.isRegularFile(normalized, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(normalized)) {
            throw new IllegalArgumentException(label + " must be a regular non-link file");
        }
        return normalized;
    }

    private record Archive(Map<String, Object> classes, Map<String, byte[]> resources) {
    }

    private static final class Asm {
        private final Class<?> classVisitor;
        private final Class<?> abstractInstruction;
        private final Class<?> fieldInstruction;
        private final Class<?> methodInstruction;
        private final Class<?> typeInstruction;
        private final Class<?> variableInstruction;
        private final Constructor<?> classReader;
        private final Constructor<?> classWriter;
        private final Constructor<?> classNode;
        private final Constructor<?> methodNode;
        private final Constructor<?> variableNode;
        private final Constructor<?> methodNodeInstruction;
        private final Constructor<?> simpleInstruction;
        private final Method readerAccept;
        private final Method classAccept;
        private final Method writerBytes;
        private final Method firstInstruction;
        private final Method previousInstruction;
        private final Method nextInstruction;
        private final Method instructionOpcode;
        private final Method addInstruction;

        Asm(ClassLoader loader) throws ReflectiveOperationException {
            Class<?> classReaderType = loader.loadClass("org.objectweb.asm.ClassReader");
            Class<?> classWriterType = loader.loadClass("org.objectweb.asm.ClassWriter");
            classVisitor = loader.loadClass("org.objectweb.asm.ClassVisitor");
            Class<?> classNodeType = loader.loadClass("org.objectweb.asm.tree.ClassNode");
            Class<?> methodNodeType = loader.loadClass("org.objectweb.asm.tree.MethodNode");
            Class<?> instructionList = loader.loadClass("org.objectweb.asm.tree.InsnList");
            abstractInstruction = loader.loadClass("org.objectweb.asm.tree.AbstractInsnNode");
            fieldInstruction = loader.loadClass("org.objectweb.asm.tree.FieldInsnNode");
            methodInstruction = loader.loadClass("org.objectweb.asm.tree.MethodInsnNode");
            typeInstruction = loader.loadClass("org.objectweb.asm.tree.TypeInsnNode");
            variableInstruction = loader.loadClass("org.objectweb.asm.tree.VarInsnNode");
            Class<?> simpleInstructionType = loader.loadClass("org.objectweb.asm.tree.InsnNode");
            classReader = classReaderType.getConstructor(byte[].class);
            classWriter = classWriterType.getConstructor(int.class);
            classNode = classNodeType.getConstructor();
            methodNode = methodNodeType.getConstructor(
                    int.class, String.class, String.class, String.class, String[].class);
            variableNode = variableInstruction.getConstructor(int.class, int.class);
            methodNodeInstruction = methodInstruction.getConstructor(
                    int.class, String.class, String.class, String.class, boolean.class);
            simpleInstruction = simpleInstructionType.getConstructor(int.class);
            readerAccept = classReaderType.getMethod("accept", classVisitor, int.class);
            classAccept = classNodeType.getMethod("accept", classVisitor);
            writerBytes = classWriterType.getMethod("toByteArray");
            firstInstruction = instructionList.getMethod("getFirst");
            previousInstruction = abstractInstruction.getMethod("getPrevious");
            nextInstruction = abstractInstruction.getMethod("getNext");
            instructionOpcode = abstractInstruction.getMethod("getOpcode");
            addInstruction = instructionList.getMethod("add", abstractInstruction);
        }

        Object readClass(byte[] bytes) throws ReflectiveOperationException {
            Object reader = classReader.newInstance((Object) bytes);
            Object node = classNode.newInstance();
            invoke(readerAccept, reader, node, 0);
            return node;
        }

        String className(Object node) throws ReflectiveOperationException {
            return (String) value(node, "name");
        }

        byte[] writeClass(Object node) throws ReflectiveOperationException {
            Object writer = classWriter.newInstance(3);
            invoke(classAccept, node, writer);
            return (byte[]) invoke(writerBytes, writer);
        }

        int repair(Map<String, Object> classes) throws ReflectiveOperationException {
            int repairs = 0;
            for (Object owner : classes.values()) {
                String superName = (String) value(owner, "superName");
                for (Object method : List.copyOf(methods(owner))) {
                    if ("<init>".equals(value(method, "name"))) {
                        Object instruction = first(method);
                        while (instruction != null) {
                            if (isMethodCall(instruction, INVOKESPECIAL, "<init>", null)) {
                                String callOwner = (String) value(instruction, "owner");
                                if (!superName.equals(callOwner)) {
                                    set(instruction, "owner", superName);
                                    repairs++;
                                }
                                break;
                            }
                            instruction = next(instruction);
                        }
                    }
                    repairs += repairDirectStores(method, classes);
                    repairs += repairStaticLocalStores(method, classes);
                    repairs += repairInstanceLocalStores(method, classes);
                }
            }
            return repairs;
        }

        private int repairDirectStores(Object method, Map<String, Object> classes)
                throws ReflectiveOperationException {
            int repairs = 0;
            for (Object instruction = first(method); instruction != null; instruction = next(instruction)) {
                if (!fieldInstruction.isInstance(instruction)) {
                    continue;
                }
                int opcode = opcode(instruction);
                String descriptor = (String) value(instruction, "desc");
                if ((opcode != PUTFIELD && opcode != PUTSTATIC) || !objectDescriptor(descriptor)) {
                    continue;
                }
                Object call = previousMeaningful(instruction);
                Object duplicate = previousMeaningful(call);
                Object created = previousMeaningful(duplicate);
                String target = descriptor.substring(1, descriptor.length() - 1);
                if (classes.containsKey(target) && objectConstruction(created, duplicate, call)) {
                    replaceConstruction(created, call, target, classes.get(target));
                    repairs++;
                }
            }
            return repairs;
        }

        private int repairStaticLocalStores(Object method, Map<String, Object> classes)
                throws ReflectiveOperationException {
            int repairs = 0;
            for (Object instruction = first(method); instruction != null; instruction = next(instruction)) {
                if (!fieldInstruction.isInstance(instruction) || opcode(instruction) != PUTSTATIC) {
                    continue;
                }
                String descriptor = (String) value(instruction, "desc");
                if (!objectDescriptor(descriptor)) {
                    continue;
                }
                Object load = previousMeaningful(instruction);
                String target = descriptor.substring(1, descriptor.length() - 1);
                repairs += repairLocalConstruction(load, target, classes.get(target));
            }
            return repairs;
        }

        private int repairInstanceLocalStores(Object method, Map<String, Object> classes)
                throws ReflectiveOperationException {
            int repairs = 0;
            for (Object instruction = first(method); instruction != null; instruction = next(instruction)) {
                if (!fieldInstruction.isInstance(instruction) || opcode(instruction) != PUTFIELD) {
                    continue;
                }
                String target = (String) value(instruction, "owner");
                Object targetClass = classes.get(target);
                if (targetClass == null) {
                    continue;
                }
                Object cursor = previousMeaningful(instruction);
                for (int distance = 0; cursor != null && distance < 32; distance++) {
                    if (variableInstruction.isInstance(cursor) && opcode(cursor) == ALOAD &&
                            repairLocalConstruction(cursor, target, targetClass) > 0) {
                        repairs++;
                        break;
                    }
                    cursor = previousMeaningful(cursor);
                }
            }
            return repairs;
        }

        private int repairLocalConstruction(Object load, String target, Object targetClass)
                throws ReflectiveOperationException {
            if (targetClass == null || !variableInstruction.isInstance(load) || opcode(load) != ALOAD) {
                return 0;
            }
            int variable = (int) value(load, "var");
            Object cursor = previousMeaningful(load);
            for (int distance = 0; cursor != null && distance < 64; distance++) {
                if (variableInstruction.isInstance(cursor) && opcode(cursor) == ASTORE &&
                        (int) value(cursor, "var") == variable) {
                    Object call = previousMeaningful(cursor);
                    Object duplicate = previousMeaningful(call);
                    Object created = previousMeaningful(duplicate);
                    if (objectConstruction(created, duplicate, call)) {
                        replaceConstruction(created, call, target, targetClass);
                        return 1;
                    }
                    return 0;
                }
                cursor = previousMeaningful(cursor);
            }
            return 0;
        }

        private boolean objectConstruction(Object created, Object duplicate, Object call)
                throws ReflectiveOperationException {
            return typeInstruction.isInstance(created) && opcode(created) == NEW &&
                    "java/lang/Object".equals(value(created, "desc")) && duplicate != null &&
                    opcode(duplicate) == DUP && isMethodCall(call, INVOKESPECIAL, "<init>", "java/lang/Object") &&
                    "()V".equals(value(call, "desc"));
        }

        private void replaceConstruction(Object created, Object call, String target, Object targetClass)
                throws ReflectiveOperationException {
            set(created, "desc", target);
            set(call, "owner", target);
            ensureDefaultConstructor(targetClass);
        }

        private void ensureDefaultConstructor(Object node) throws ReflectiveOperationException {
            for (Object method : methods(node)) {
                if ("<init>".equals(value(method, "name")) && "()V".equals(value(method, "desc"))) {
                    return;
                }
            }
            Object constructor = methodNode.newInstance(ACC_PUBLIC, "<init>", "()V", null, null);
            Object instructions = value(constructor, "instructions");
            invoke(addInstruction, instructions, variableNode.newInstance(ALOAD, 0));
            invoke(addInstruction, instructions, methodNodeInstruction.newInstance(
                    INVOKESPECIAL, value(node, "superName"), "<init>", "()V", false));
            invoke(addInstruction, instructions, simpleInstruction.newInstance(RETURN));
            methods(node).add(constructor);
        }

        @SuppressWarnings("unchecked")
        private List<Object> methods(Object node) throws ReflectiveOperationException {
            return (List<Object>) value(node, "methods");
        }

        private Object first(Object method) throws ReflectiveOperationException {
            return invoke(firstInstruction, value(method, "instructions"));
        }

        private Object next(Object instruction) throws ReflectiveOperationException {
            return invoke(nextInstruction, instruction);
        }

        private Object previousMeaningful(Object instruction) throws ReflectiveOperationException {
            Object current = instruction == null ? null : invoke(previousInstruction, instruction);
            while (current != null && opcode(current) < 0) {
                current = invoke(previousInstruction, current);
            }
            return current;
        }

        private int opcode(Object instruction) throws ReflectiveOperationException {
            return (int) invoke(instructionOpcode, instruction);
        }

        private boolean isMethodCall(Object instruction, int opcode, String name, String owner)
                throws ReflectiveOperationException {
            return methodInstruction.isInstance(instruction) && opcode(instruction) == opcode &&
                    name.equals(value(instruction, "name")) &&
                    (owner == null || owner.equals(value(instruction, "owner")));
        }

        private static boolean objectDescriptor(String descriptor) {
            return descriptor != null && descriptor.startsWith("L") && descriptor.endsWith(";");
        }

        private static Object value(Object target, String name) throws ReflectiveOperationException {
            return publicField(target, name).get(target);
        }

        private static void set(Object target, String name, Object value) throws ReflectiveOperationException {
            publicField(target, name).set(target, value);
        }

        private static Field publicField(Object target, String name) throws NoSuchFieldException {
            return target.getClass().getField(name);
        }

        private static Object invoke(Method method, Object target, Object... arguments)
                throws ReflectiveOperationException {
            try {
                return method.invoke(target, arguments);
            } catch (InvocationTargetException exception) {
                Throwable cause = exception.getCause();
                if (cause instanceof ReflectiveOperationException reflective) {
                    throw reflective;
                }
                if (cause instanceof RuntimeException runtime) {
                    throw runtime;
                }
                throw new IllegalStateException(cause);
            }
        }
    }
}
