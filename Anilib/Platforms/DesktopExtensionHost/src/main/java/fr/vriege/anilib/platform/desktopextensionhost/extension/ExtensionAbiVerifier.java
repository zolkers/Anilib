package fr.vriege.anilib.platform.desktopextensionhost.extension;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Handle;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import org.objectweb.asm.Label;

/* Validates every relocated host ABI symbol before an extension is activated. */
public final class ExtensionAbiVerifier {
    private static final String COMPATIBILITY_PREFIX =
            "fr/vriege/anilib/platform/desktopextensionhost/compat/";
    private static final int MAX_DIAGNOSTICS = 100;

    private final ClassLoader hostLoader;
    private final Map<String, Class<?>> hostClasses = new HashMap<>();
    private final Set<String> unavailableClasses = new HashSet<>();

    public ExtensionAbiVerifier() {
        this(ExtensionAbiVerifier.class.getClassLoader());
    }

    ExtensionAbiVerifier(ClassLoader hostLoader) {
        this.hostLoader = hostLoader;
    }

    public Report inspect(Path archive) {
        Path source = archive.toAbsolutePath().normalize();
        if (!Files.isRegularFile(source, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(source)) {
            throw new IllegalArgumentException("Extension archive must be a regular non-link JAR");
        }
        Set<String> missing = new LinkedHashSet<>();
        try (JarFile jar = new JarFile(source.toFile())) {
            var entries = jar.entries();
            while (entries.hasMoreElements() && missing.size() < MAX_DIAGNOSTICS) {
                JarEntry entry = entries.nextElement();
                if (entry.isDirectory() || !entry.getName().endsWith(".class")) {
                    continue;
                }
                try (InputStream input = jar.getInputStream(entry)) {
                    new ClassReader(input).accept(visitor(missing), ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
                }
            }
        } catch (IOException exception) {
            throw new IllegalArgumentException("Unable to inspect extension ABI", exception);
        }
        return new Report(missing.stream().sorted().toList());
    }

    public void requireCompatible(Path archive) {
        Report report = inspect(archive);
        if (!report.compatible()) {
            throw new IncompatibleExtensionAbiException(report.missingSymbols());
        }
    }

    private ClassVisitor visitor(Set<String> missing) {
        return new ClassVisitor(Opcodes.ASM9) {
            @Override
            public void visit(
                    int version,
                    int access,
                    String name,
                    String signature,
                    String superName,
                    String[] interfaces) {
                checkClass(superName, missing);
                if (interfaces != null) {
                    for (String contract : interfaces) {
                        checkClass(contract, missing);
                    }
                }
            }

            @Override
            public FieldVisitor visitField(
                    int access,
                    String name,
                    String descriptor,
                    String signature,
                    Object value) {
                checkType(Type.getType(descriptor), missing);
                return null;
            }

            @Override
            public MethodVisitor visitMethod(
                    int access,
                    String name,
                    String descriptor,
                    String signature,
                    String[] exceptions) {
                checkMethodType(descriptor, missing);
                if (exceptions != null) {
                    for (String exception : exceptions) {
                        checkClass(exception, missing);
                    }
                }
                return methodVisitor(missing);
            }
        };
    }

    private MethodVisitor methodVisitor(Set<String> missing) {
        return new MethodVisitor(Opcodes.ASM9) {
            @Override
            public void visitTypeInsn(int opcode, String type) {
                checkClass(type, missing);
            }

            @Override
            public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
                checkType(Type.getType(descriptor), missing);
                if (compatibilityType(owner) && !fieldAvailable(owner, name, descriptor)) {
                    add(missing, "field " + binaryName(owner) + '.' + name + ' ' + descriptor);
                }
            }

            @Override
            public void visitMethodInsn(
                    int opcode,
                    String owner,
                    String name,
                    String descriptor,
                    boolean isInterface) {
                checkMethodType(descriptor, missing);
                if (compatibilityType(owner) && !methodAvailable(owner, name, descriptor)) {
                    add(missing, "method " + binaryName(owner) + '.' + name + descriptor);
                }
            }

            @Override
            public void visitInvokeDynamicInsn(
                    String name,
                    String descriptor,
                    Handle bootstrapMethodHandle,
                    Object... bootstrapMethodArguments) {
                checkMethodType(descriptor, missing);
                checkHandle(bootstrapMethodHandle, missing);
                for (Object argument : bootstrapMethodArguments) {
                    if (argument instanceof Handle handle) {
                        checkHandle(handle, missing);
                    } else if (argument instanceof Type type) {
                        checkType(type, missing);
                    }
                }
            }

            @Override
            public void visitLdcInsn(Object value) {
                if (value instanceof Type type) {
                    checkType(type, missing);
                } else if (value instanceof Handle handle) {
                    checkHandle(handle, missing);
                }
            }

            @Override
            public void visitMultiANewArrayInsn(String descriptor, int dimensions) {
                checkType(Type.getType(descriptor), missing);
            }

            @Override
            public void visitTryCatchBlock(
                    Label start,
                    Label end,
                    Label handler,
                    String type) {
                checkClass(type, missing);
            }
        };
    }

    private void checkHandle(Handle handle, Set<String> missing) {
        int tag = handle.getTag();
        if (tag == Opcodes.H_GETFIELD || tag == Opcodes.H_GETSTATIC
                || tag == Opcodes.H_PUTFIELD || tag == Opcodes.H_PUTSTATIC) {
            if (compatibilityType(handle.getOwner())
                    && !fieldAvailable(handle.getOwner(), handle.getName(), handle.getDesc())) {
                add(missing, "field " + binaryName(handle.getOwner()) + '.'
                        + handle.getName() + ' ' + handle.getDesc());
            }
        } else if (compatibilityType(handle.getOwner())
                && !methodAvailable(handle.getOwner(), handle.getName(), handle.getDesc())) {
            add(missing, "method " + binaryName(handle.getOwner()) + '.'
                    + handle.getName() + handle.getDesc());
        }
    }

    private void checkMethodType(String descriptor, Set<String> missing) {
        Type type = Type.getMethodType(descriptor);
        checkType(type.getReturnType(), missing);
        for (Type argument : type.getArgumentTypes()) {
            checkType(argument, missing);
        }
    }

    private void checkType(Type type, Set<String> missing) {
        if (type.getSort() == Type.ARRAY) {
            checkType(type.getElementType(), missing);
        } else if (type.getSort() == Type.OBJECT) {
            checkClass(type.getInternalName(), missing);
        } else if (type.getSort() == Type.METHOD) {
            checkMethodType(type.getDescriptor(), missing);
        }
    }

    private void checkClass(String internalName, Set<String> missing) {
        if (compatibilityType(internalName) && hostClass(internalName) == null) {
            add(missing, "class " + binaryName(internalName));
        }
    }

    private boolean methodAvailable(String owner, String name, String descriptor) {
        Class<?> type = hostClass(owner);
        if (type == null) {
            return false;
        }
        if ("<init>".equals(name)) {
            for (Constructor<?> constructor : type.getDeclaredConstructors()) {
                if (Type.getConstructorDescriptor(constructor).equals(descriptor)) {
                    return true;
                }
            }
            return false;
        }
        return methods(type).stream().anyMatch(method -> method.getName().equals(name)
                && Type.getMethodDescriptor(method).equals(descriptor));
    }

    private boolean fieldAvailable(String owner, String name, String descriptor) {
        Class<?> type = hostClass(owner);
        if (type == null) {
            return false;
        }
        return fields(type).stream().anyMatch(field -> field.getName().equals(name)
                && Type.getDescriptor(field.getType()).equals(descriptor));
    }

    private List<Method> methods(Class<?> type) {
        List<Method> result = new ArrayList<>();
        Set<Class<?>> visited = new HashSet<>();
        collectMethods(type, result, visited);
        return result;
    }

    private void collectMethods(Class<?> type, List<Method> target, Set<Class<?>> visited) {
        if (type == null || !visited.add(type)) {
            return;
        }
        target.addAll(List.of(type.getDeclaredMethods()));
        collectMethods(type.getSuperclass(), target, visited);
        for (Class<?> contract : type.getInterfaces()) {
            collectMethods(contract, target, visited);
        }
    }

    private List<Field> fields(Class<?> type) {
        List<Field> result = new ArrayList<>();
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            result.addAll(List.of(current.getDeclaredFields()));
        }
        return result;
    }

    private Class<?> hostClass(String internalName) {
        if (!compatibilityType(internalName)) {
            return null;
        }
        if (unavailableClasses.contains(internalName)) {
            return null;
        }
        Class<?> cached = hostClasses.get(internalName);
        if (cached != null) {
            return cached;
        }
        try {
            Class<?> loaded = Class.forName(binaryName(internalName), false, hostLoader);
            hostClasses.put(internalName, loaded);
            return loaded;
        } catch (ClassNotFoundException | LinkageError exception) {
            unavailableClasses.add(internalName);
            return null;
        }
    }

    private static boolean compatibilityType(String internalName) {
        return internalName != null && internalName.startsWith(COMPATIBILITY_PREFIX);
    }

    private static String binaryName(String internalName) {
        return internalName.replace('/', '.');
    }

    private static void add(Set<String> missing, String diagnostic) {
        if (missing.size() < MAX_DIAGNOSTICS) {
            missing.add(diagnostic);
        }
    }

    public record Report(List<String> missingSymbols) {
        public Report {
            missingSymbols = missingSymbols.stream().sorted(Comparator.naturalOrder()).toList();
        }

        public boolean compatible() {
            return missingSymbols.isEmpty();
        }
    }

    public static final class IncompatibleExtensionAbiException extends RuntimeException {
        private static final long serialVersionUID = 1L;
        private final transient List<String> missingSymbols;

        private IncompatibleExtensionAbiException(List<String> missingSymbols) {
            super("Missing desktop host ABI: " + String.join(", ", missingSymbols));
            this.missingSymbols = List.copyOf(missingSymbols);
        }

        public List<String> missingSymbols() {
            return missingSymbols;
        }
    }
}
