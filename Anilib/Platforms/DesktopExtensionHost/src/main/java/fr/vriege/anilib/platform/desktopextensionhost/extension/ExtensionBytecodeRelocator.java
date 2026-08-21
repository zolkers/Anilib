package fr.vriege.anilib.platform.desktopextensionhost.extension;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.Arrays;
import java.util.Locale;

public final class ExtensionBytecodeRelocator {
    static final String FORMAT = "2";
    private static final int MAX_ENTRIES = 25_000;
    private static final long MAX_EXPANDED_BYTES = 256L * 1024L * 1024L;
    private static final String TARGET = "fr/vriege/anilib/platform/desktopextensionhost/compat/";
    private static final Map<String, String> TYPES = Map.of(
            "eu/kanade/tachiyomi/AppInfo", TARGET + "aniyomi/AppInfo");
    private static final Map<String, String> PREFIXES = Map.ofEntries(
            Map.entry("eu/kanade/tachiyomi/source/", TARGET + "aniyomi/source/"),
            Map.entry("eu/kanade/tachiyomi/animesource/", TARGET + "aniyomi/animesource/"),
            Map.entry("eu/kanade/tachiyomi/network/", TARGET + "aniyomi/network/"),
            Map.entry("eu/kanade/tachiyomi/util/", TARGET + "aniyomi/util/"),
            Map.entry("eu/kanade/tachiyomi/torrentutils/", TARGET + "aniyomi/torrentutils/"),
            Map.entry("tachiyomi/core/", TARGET + "tachiyomi/core/"),
            Map.entry("aniyomi/core/", TARGET + "aniyomi/core/"),
            Map.entry("androidx/preference/", TARGET + "androidx/preference/"),
            Map.entry("android/", TARGET + "android/"),
            Map.entry("uy/kohesive/injekt/", TARGET + "injekt/"),
            Map.entry("app/cash/quickjs/", TARGET + "quickjs/"),
            Map.entry("com/squareup/duktape/", TARGET + "duktape/"),
            Map.entry("logcat/", TARGET + "logcat/"),
            Map.entry("mihon/core/", TARGET + "mihon/core/"));
    private static final List<String> FORBIDDEN = List.copyOf(PREFIXES.keySet());

    public RelocationResult relocate(Path inputJar, Path outputJar) {
        Path input = inputJar.toAbsolutePath().normalize();
        Path output = outputJar.toAbsolutePath().normalize();
        Map<String, byte[]> entries = readEntries(input);
        ClassHierarchy hierarchy = ClassHierarchy.from(entries);
        Map<String, ClassNode> classes = readClasses(entries);
        Map<String, byte[]> transformed = new LinkedHashMap<>();
        Set<String> unresolved = new HashSet<>();
        int relocatedClasses = 0;
        int repairs = repairConvertedClasses(classes);
        for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
            String name = entry.getKey();
            byte[] bytes = entry.getValue();
            if (!name.endsWith(".class")) {
                if (!signature(name)) {
                    putUnique(transformed, name, bytes);
                }
                continue;
            }
            ClassNode node = classes.get(new ClassReader(bytes).getClassName());
            Transformation transformation = transform(node, hierarchy);
            if (!Arrays.equals(bytes, transformation.bytes())) {
                relocatedClasses++;
            }
            collectUnresolved(transformation.bytes(), unresolved);
            String className = new ClassReader(transformation.bytes()).getClassName() + ".class";
            putUnique(transformed, className, transformation.bytes());
        }
        putUnique(transformed, "META-INF/anilib-desktop-extension.properties",
                ("format=" + FORMAT + "\nrelocatedClasses=" + relocatedClasses + "\nrepairs=" + repairs + "\n")
                        .getBytes(StandardCharsets.UTF_8));
        writeEntries(output, transformed);
        return new RelocationResult(relocatedClasses, repairs, unresolved.stream().sorted().toList());
    }

    private static Transformation transform(ClassNode node, ClassHierarchy hierarchy) {
        ClassWriter writer = new CompatibilityClassWriter(hierarchy);
        node.accept(new ClassRemapper(writer, new AbiRemapper()));
        return new Transformation(writer.toByteArray());
    }

    private static Map<String, ClassNode> readClasses(Map<String, byte[]> entries) {
        Map<String, ClassNode> result = new LinkedHashMap<>();
        for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
            if (!entry.getKey().endsWith(".class")) {
                continue;
            }
            ClassNode node = new ClassNode();
            new ClassReader(entry.getValue()).accept(node, 0);
            result.put(node.name, node);
        }
        return result;
    }

    private static int repairConvertedClasses(Map<String, ClassNode> classes) {
        int repairs = 0;
        for (ClassNode node : classes.values()) {
            repairs += repairEnumConstants(node);
            repairs += repairWrongConstructorOwners(node, classes);
            repairs += repairDirectObjectStores(node, classes);
            repairs += repairLocalObjectStores(node, classes);
            repairs += repairBrokenStringLazyInitializers(node, classes);
        }
        return repairs;
    }

    private static int repairEnumConstants(ClassNode owner) {
        if ((owner.access & Opcodes.ACC_ENUM) == 0 || !"java/lang/Enum".equals(owner.superName)) {
            return 0;
        }
        int repairs = 0;
        for (MethodNode method : List.copyOf(owner.methods)) {
            if (!"<clinit>".equals(method.name)) {
                continue;
            }
            for (AbstractInsnNode instruction : method.instructions) {
                if (instruction instanceof TypeInsnNode created
                        && created.getOpcode() == Opcodes.NEW
                        && "java/lang/Enum".equals(created.desc)) {
                    created.desc = owner.name;
                    repairs++;
                } else if (instruction instanceof MethodInsnNode call
                        && call.getOpcode() == Opcodes.INVOKESPECIAL
                        && "<init>".equals(call.name)
                        && "java/lang/Enum".equals(call.owner)) {
                    call.owner = owner.name;
                    ensureEnumConstructor(owner, call.desc);
                    repairs++;
                }
            }
        }
        return repairs;
    }

    private static void ensureEnumConstructor(ClassNode owner, String descriptor) {
        for (MethodNode method : owner.methods) {
            if ("<init>".equals(method.name) && descriptor.equals(method.desc)) {
                return;
            }
        }
        Type[] arguments = Type.getArgumentTypes(descriptor);
        if (arguments.length < 2
                || arguments[0].getSort() != Type.OBJECT
                || !"java/lang/String".equals(arguments[0].getInternalName())
                || arguments[1].getSort() != Type.INT) {
            throw new IllegalArgumentException("Converted enum has an invalid constructor descriptor");
        }
        MethodNode constructor = new MethodNode(Opcodes.ACC_PRIVATE, "<init>", descriptor, null, null);
        constructor.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        constructor.instructions.add(new VarInsnNode(Opcodes.ALOAD, 1));
        constructor.instructions.add(new VarInsnNode(Opcodes.ILOAD, 2));
        constructor.instructions.add(new MethodInsnNode(
                Opcodes.INVOKESPECIAL, "java/lang/Enum", "<init>", "(Ljava/lang/String;I)V", false));
        constructor.instructions.add(new InsnNode(Opcodes.RETURN));
        owner.methods.add(constructor);
    }

    private static final class CompatibilityClassWriter extends ClassWriter {
        private final ClassHierarchy hierarchy;

        private CompatibilityClassWriter(ClassHierarchy hierarchy) {
            super(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
            this.hierarchy = hierarchy;
        }

        @Override
        protected String getCommonSuperClass(String first, String second) {
            return hierarchy.commonSuperClass(first, second);
        }
    }

    private static final class ClassHierarchy {
        private final Map<String, ClassInfo> classes;

        private ClassHierarchy(Map<String, ClassInfo> classes) {
            this.classes = Map.copyOf(classes);
        }

        private static ClassHierarchy from(Map<String, byte[]> entries) {
            Map<String, ClassInfo> result = new LinkedHashMap<>();
            AbiRemapper remapper = new AbiRemapper();
            for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                if (!entry.getKey().endsWith(".class")) {
                    continue;
                }
                ClassReader reader = new ClassReader(entry.getValue());
                String name = remapper.mapType(reader.getClassName());
                String parent = reader.getSuperName() == null ? null : remapper.mapType(reader.getSuperName());
                List<String> interfaces = Arrays.stream(reader.getInterfaces())
                        .map(remapper::mapType)
                        .toList();
                result.put(name, new ClassInfo(parent, interfaces, (reader.getAccess() & Opcodes.ACC_INTERFACE) != 0));
            }
            return new ClassHierarchy(result);
        }

        private String commonSuperClass(String first, String second) {
            if (assignable(first, second, new HashSet<>())) {
                return first;
            }
            if (assignable(second, first, new HashSet<>())) {
                return second;
            }
            if (interfaceType(first) || interfaceType(second)) {
                return "java/lang/Object";
            }
            String candidate = parent(first);
            while (candidate != null) {
                if (assignable(candidate, second, new HashSet<>())) {
                    return candidate;
                }
                candidate = parent(candidate);
            }
            return "java/lang/Object";
        }

        private boolean assignable(String target, String source, Set<String> visited) {
            if (target.equals(source)) {
                return true;
            }
            if (!visited.add(source)) {
                return false;
            }
            ClassInfo info = classes.get(source);
            if (info != null) {
                if (info.parent() != null && assignable(target, info.parent(), visited)) {
                    return true;
                }
                for (String contract : info.interfaces()) {
                    if (assignable(target, contract, visited)) {
                        return true;
                    }
                }
                return false;
            }
            try {
                ClassLoader loader = ExtensionBytecodeRelocator.class.getClassLoader();
                Class<?> targetType = Class.forName(target.replace('/', '.'), false, loader);
                Class<?> sourceType = Class.forName(source.replace('/', '.'), false, loader);
                return targetType.isAssignableFrom(sourceType);
            } catch (ClassNotFoundException | LinkageError ignored) {
                return "java/lang/Object".equals(target);
            }
        }

        private boolean interfaceType(String name) {
            ClassInfo info = classes.get(name);
            if (info != null) {
                return info.interfaceType();
            }
            try {
                return Class.forName(name.replace('/', '.'), false,
                        ExtensionBytecodeRelocator.class.getClassLoader()).isInterface();
            } catch (ClassNotFoundException | LinkageError ignored) {
                return false;
            }
        }

        private String parent(String name) {
            ClassInfo info = classes.get(name);
            if (info != null) {
                return info.parent();
            }
            try {
                Class<?> parent = Class.forName(name.replace('/', '.'), false,
                        ExtensionBytecodeRelocator.class.getClassLoader()).getSuperclass();
                return parent == null ? null : parent.getName().replace('.', '/');
            } catch (ClassNotFoundException | LinkageError ignored) {
                return "java/lang/Object".equals(name) ? null : "java/lang/Object";
            }
        }
    }

    private record ClassInfo(String parent, List<String> interfaces, boolean interfaceType) {
    }

    private static int repairWrongConstructorOwners(ClassNode owner, Map<String, ClassNode> classes) {
        int repairs = 0;
        for (MethodNode method : List.copyOf(owner.methods)) {
            if ("<init>".equals(method.name)) {
                for (AbstractInsnNode instruction : method.instructions) {
                    if (instruction instanceof MethodInsnNode call
                            && call.getOpcode() == Opcodes.INVOKESPECIAL
                            && "<init>".equals(call.name)) {
                        if ("java/lang/Object".equals(call.owner)
                                && !"java/lang/Object".equals(owner.superName)) {
                            call.owner = owner.superName;
                            ClassNode parent = classes.get(owner.superName);
                            if (parent != null && "()V".equals(call.desc)) {
                                ensureDefaultConstructor(parent);
                            }
                            repairs++;
                        }
                        break;
                    }
                }
            }
            ArrayDeque<TypeInsnNode> pending = new ArrayDeque<>();
            TypeInsnNode created = null;
            for (AbstractInsnNode instruction : method.instructions) {
                if (instruction instanceof TypeInsnNode type && type.getOpcode() == Opcodes.NEW) {
                    created = type;
                } else if (instruction instanceof InsnNode simple
                        && simple.getOpcode() == Opcodes.DUP && created != null) {
                    pending.push(created);
                    created = null;
                } else if (instruction instanceof MethodInsnNode call
                        && call.getOpcode() == Opcodes.INVOKESPECIAL
                        && "<init>".equals(call.name)
                        && !pending.isEmpty()) {
                    String expectedOwner = pending.pop().desc;
                    if (!expectedOwner.equals(call.owner)) {
                        call.owner = expectedOwner;
                        repairs++;
                    }
                }
            }
        }
        return repairs;
    }

    private static int repairDirectObjectStores(ClassNode owner, Map<String, ClassNode> classes) {
        int repairs = 0;
        for (MethodNode method : List.copyOf(owner.methods)) {
            for (AbstractInsnNode instruction : method.instructions) {
                if (!(instruction instanceof FieldInsnNode field)
                        || (field.getOpcode() != Opcodes.PUTFIELD && field.getOpcode() != Opcodes.PUTSTATIC)
                        || !field.desc.startsWith("L") || !field.desc.endsWith(";")) {
                    continue;
                }
                AbstractInsnNode callNode = previousMeaningful(field);
                AbstractInsnNode duplicate = previousMeaningful(callNode);
                AbstractInsnNode createdNode = previousMeaningful(duplicate);
                String target = field.desc.substring(1, field.desc.length() - 1);
                ClassNode targetClass = classes.get(target);
                if (targetClass == null
                        || !(createdNode instanceof TypeInsnNode created)
                        || created.getOpcode() != Opcodes.NEW
                        || !"java/lang/Object".equals(created.desc)
                        || duplicate == null || duplicate.getOpcode() != Opcodes.DUP
                        || !(callNode instanceof MethodInsnNode call)
                        || call.getOpcode() != Opcodes.INVOKESPECIAL
                        || !"<init>".equals(call.name)
                        || !"java/lang/Object".equals(call.owner)
                        || !"()V".equals(call.desc)) {
                    continue;
                }
                created.desc = target;
                call.owner = target;
                ensureDefaultConstructor(targetClass);
                repairs++;
            }
        }
        return repairs;
    }

    private static int repairLocalObjectStores(ClassNode owner, Map<String, ClassNode> classes) {
        int repairs = 0;
        for (MethodNode method : List.copyOf(owner.methods)) {
            for (AbstractInsnNode instruction : method.instructions) {
                if (!(instruction instanceof FieldInsnNode field)
                        || (field.getOpcode() != Opcodes.PUTFIELD && field.getOpcode() != Opcodes.PUTSTATIC)) {
                    continue;
                }
                String target = field.getOpcode() == Opcodes.PUTSTATIC
                        && field.desc.startsWith("L") && field.desc.endsWith(";")
                        ? field.desc.substring(1, field.desc.length() - 1)
                        : field.owner;
                ClassNode targetClass = classes.get(target);
                if (targetClass == null) {
                    continue;
                }
                AbstractInsnNode cursor = previousMeaningful(field);
                int remaining = field.getOpcode() == Opcodes.PUTSTATIC ? 1 : 32;
                while (cursor != null && remaining-- > 0) {
                    if (cursor instanceof VarInsnNode load && load.getOpcode() == Opcodes.ALOAD
                            && repairLocalConstruction(load, target, targetClass)) {
                        repairs++;
                        break;
                    }
                    cursor = previousMeaningful(cursor);
                }
            }
        }
        return repairs;
    }

    private static boolean repairLocalConstruction(
            VarInsnNode load,
            String target,
            ClassNode targetClass) {
        AbstractInsnNode cursor = previousMeaningful(load);
        for (int distance = 0; cursor != null && distance < 64; distance++) {
            if (cursor instanceof VarInsnNode store
                    && store.getOpcode() == Opcodes.ASTORE && store.var == load.var) {
                AbstractInsnNode callNode = previousMeaningful(store);
                AbstractInsnNode duplicate = previousMeaningful(callNode);
                AbstractInsnNode createdNode = previousMeaningful(duplicate);
                if (objectConstruction(createdNode, duplicate, callNode)) {
                    TypeInsnNode created = (TypeInsnNode) createdNode;
                    MethodInsnNode call = (MethodInsnNode) callNode;
                    created.desc = target;
                    call.owner = target;
                    ensureDefaultConstructor(targetClass);
                    return true;
                }
                return false;
            }
            cursor = previousMeaningful(cursor);
        }
        return false;
    }

    private static boolean objectConstruction(
            AbstractInsnNode created,
            AbstractInsnNode duplicate,
            AbstractInsnNode call) {
        return created instanceof TypeInsnNode type
                && type.getOpcode() == Opcodes.NEW
                && "java/lang/Object".equals(type.desc)
                && duplicate != null && duplicate.getOpcode() == Opcodes.DUP
                && call instanceof MethodInsnNode method
                && method.getOpcode() == Opcodes.INVOKESPECIAL
                && "<init>".equals(method.name)
                && "java/lang/Object".equals(method.owner)
                && "()V".equals(method.desc);
    }

    private static int repairBrokenStringLazyInitializers(
            ClassNode owner,
            Map<String, ClassNode> classes) {
        if (owner.methods.stream().noneMatch(method -> "getBaseUrl".equals(method.name)
                && "()Ljava/lang/String;".equals(method.desc))) {
            return 0;
        }
        int repairs = 0;
        for (MethodNode method : owner.methods) {
            if (!"<init>".equals(method.name)) {
                continue;
            }
            for (AbstractInsnNode instruction : method.instructions.toArray()) {
                if (!(instruction instanceof TypeInsnNode created)
                        || created.getOpcode() != Opcodes.NEW
                        || !"java/lang/Object".equals(created.desc)) {
                    continue;
                }
                AbstractInsnNode duplicate = nextMeaningful(created);
                AbstractInsnNode constructor = nextMeaningful(duplicate);
                AbstractInsnNode lazyCall = nextMeaningful(constructor);
                AbstractInsnNode store = nextMeaningful(lazyCall);
                if (duplicate == null || duplicate.getOpcode() != Opcodes.DUP
                        || !(constructor instanceof MethodInsnNode initialized)
                        || initialized.getOpcode() != Opcodes.INVOKESPECIAL
                        || !"java/lang/Object".equals(initialized.owner)
                        || !"<init>".equals(initialized.name)
                        || !"()V".equals(initialized.desc)
                        || !(lazyCall instanceof MethodInsnNode lazy)
                        || lazy.getOpcode() != Opcodes.INVOKESTATIC
                        || !"kotlin/LazyKt".equals(lazy.owner)
                        || !"lazy".equals(lazy.name)
                        || !"(Lkotlin/jvm/functions/Function0;)Lkotlin/Lazy;".equals(lazy.desc)
                        || !(store instanceof FieldInsnNode field)
                        || field.getOpcode() != Opcodes.PUTFIELD
                        || !"Lkotlin/Lazy;".equals(field.desc)
                        || !stringLazyField(classes, field.owner, field.name)) {
                    continue;
                }
                InsnList replacement = new InsnList();
                replacement.add(new VarInsnNode(Opcodes.ALOAD, 0));
                replacement.add(new MethodInsnNode(
                        Opcodes.INVOKESTATIC,
                        TARGET + "aniyomi/source/online/ExtensionLazySupport",
                        "baseUrlHostInitializer",
                        "(Ljava/lang/Object;)Ljava/lang/Object;",
                        false));
                replacement.add(new TypeInsnNode(Opcodes.CHECKCAST, "kotlin/jvm/functions/Function0"));
                method.instructions.insertBefore(created, replacement);
                method.instructions.remove(created);
                method.instructions.remove(duplicate);
                method.instructions.remove(constructor);
                repairs++;
            }
        }
        return repairs;
    }

    private static boolean stringLazyField(
            Map<String, ClassNode> classes,
            String owner,
            String fieldName) {
        for (ClassNode candidate : classes.values()) {
            for (MethodNode method : candidate.methods) {
                for (AbstractInsnNode instruction : method.instructions) {
                    if (!(instruction instanceof FieldInsnNode field)
                            || field.getOpcode() != Opcodes.GETFIELD
                            || !owner.equals(field.owner)
                            || !fieldName.equals(field.name)) {
                        continue;
                    }
                    AbstractInsnNode getValue = nextMeaningful(field);
                    AbstractInsnNode cast = nextMeaningful(getValue);
                    if (getValue instanceof MethodInsnNode call
                            && call.getOpcode() == Opcodes.INVOKEINTERFACE
                            && "kotlin/Lazy".equals(call.owner)
                            && "getValue".equals(call.name)
                            && cast instanceof TypeInsnNode converted
                            && converted.getOpcode() == Opcodes.CHECKCAST
                            && "java/lang/String".equals(converted.desc)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static AbstractInsnNode nextMeaningful(AbstractInsnNode instruction) {
        AbstractInsnNode current = instruction == null ? null : instruction.getNext();
        while (current != null && current.getOpcode() < 0) {
            current = current.getNext();
        }
        return current;
    }

    private static AbstractInsnNode previousMeaningful(AbstractInsnNode instruction) {
        AbstractInsnNode current = instruction == null ? null : instruction.getPrevious();
        while (current != null && current.getOpcode() < 0) {
            current = current.getPrevious();
        }
        return current;
    }

    private static void ensureDefaultConstructor(ClassNode target) {
        for (MethodNode method : target.methods) {
            if ("<init>".equals(method.name) && "()V".equals(method.desc)) {
                return;
            }
        }
        MethodNode constructor = new MethodNode(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        constructor.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        constructor.instructions.add(new MethodInsnNode(
                Opcodes.INVOKESPECIAL, target.superName, "<init>", "()V", false));
        constructor.instructions.add(new InsnNode(Opcodes.RETURN));
        target.methods.add(constructor);
    }

    private static Map<String, byte[]> readEntries(Path jar) {
        if (!Files.isRegularFile(jar) || Files.isSymbolicLink(jar)) {
            throw new IllegalArgumentException("Converted extension must be a regular non-link JAR");
        }
        Map<String, byte[]> result = new LinkedHashMap<>();
        long expanded = 0;
        int count = 0;
        try (JarFile file = new JarFile(jar.toFile())) {
            Enumeration<JarEntry> entries = file.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (entry.isDirectory()) {
                    continue;
                }
                count++;
                if (count > MAX_ENTRIES) {
                    throw new IllegalArgumentException("Converted extension contains too many entries");
                }
                String name = safeEntryName(entry.getName());
                byte[] bytes;
                try (InputStream input = file.getInputStream(entry)) {
                    bytes = input.readNBytes(Math.toIntExact(MAX_EXPANDED_BYTES + 1));
                }
                expanded = Math.addExact(expanded, bytes.length);
                if (bytes.length > MAX_EXPANDED_BYTES || expanded > MAX_EXPANDED_BYTES) {
                    throw new IllegalArgumentException("Converted extension exceeds the expanded size limit");
                }
                putUnique(result, name, bytes);
            }
            return result;
        } catch (IOException exception) {
            throw new IllegalArgumentException("Unable to read converted extension JAR", exception);
        }
    }

    private static void writeEntries(Path output, Map<String, byte[]> entries) {
        try {
            Files.createDirectories(output.getParent());
            try (OutputStream file = Files.newOutputStream(output);
                    JarOutputStream jar = new JarOutputStream(file)) {
                for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                    JarEntry jarEntry = new JarEntry(entry.getKey());
                    jarEntry.setTime(0L);
                    jar.putNextEntry(jarEntry);
                    jar.write(entry.getValue());
                    jar.closeEntry();
                }
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to write relocated extension JAR", exception);
        }
    }

    private static String safeEntryName(String name) {
        if (name.isBlank() || name.startsWith("/") || name.startsWith("\\")
                || name.contains("../") || name.contains("..\\") || name.indexOf('\0') >= 0) {
            throw new IllegalArgumentException("Unsafe archive entry: " + name);
        }
        return name.replace('\\', '/');
    }

    private static boolean signature(String name) {
        String upper = name.toUpperCase(Locale.ROOT);
        return upper.startsWith("META-INF/")
                && (upper.endsWith(".SF") || upper.endsWith(".RSA") || upper.endsWith(".DSA"));
    }

    private static void putUnique(Map<String, byte[]> entries, String name, byte[] bytes) {
        if (entries.putIfAbsent(name, bytes) != null) {
            throw new IllegalArgumentException("Duplicate archive entry after relocation: " + name);
        }
    }

    private static void collectUnresolved(byte[] bytes, Set<String> unresolved) {
        String constantPool = new String(bytes, StandardCharsets.ISO_8859_1);
        for (String prefix : FORBIDDEN) {
            if (constantPool.contains(prefix) || constantPool.contains(prefix.replace('/', '.'))) {
                unresolved.add(prefix);
            }
        }
    }

    public record RelocationResult(int relocatedClasses, int repairedInstructions, List<String> unresolvedPrefixes) {
        public RelocationResult {
            unresolvedPrefixes = List.copyOf(unresolvedPrefixes);
        }
    }

    private record Transformation(byte[] bytes) {
    }

    private static final class AbiRemapper extends Remapper {
        private AbiRemapper() {
            super(Opcodes.ASM9);
        }

        @Override
        public String map(String internalName) {
            String type = TYPES.get(internalName);
            if (type != null) {
                return type;
            }
            for (Map.Entry<String, String> entry : PREFIXES.entrySet()) {
                if (internalName.startsWith(entry.getKey())) {
                    return entry.getValue() + internalName.substring(entry.getKey().length());
                }
            }
            return internalName;
        }

        @Override
        public Object mapValue(Object value) {
            if (value instanceof String text) {
                String internal = text.replace('.', '/');
                String mapped = map(internal);
                if (!mapped.equals(internal)) {
                    return text.indexOf('/') >= 0 ? mapped : mapped.replace('/', '.');
                }
            }
            return super.mapValue(value);
        }
    }
}
