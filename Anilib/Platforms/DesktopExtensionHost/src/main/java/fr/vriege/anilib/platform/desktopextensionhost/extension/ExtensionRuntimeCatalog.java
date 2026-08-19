package fr.vriege.anilib.platform.desktopextensionhost.extension;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class ExtensionRuntimeCatalog {
    private final ExtensionRegistry registry;

    public ExtensionRuntimeCatalog(ExtensionRegistry registry) {
        this.registry = Objects.requireNonNull(registry, "registry");
    }

    public Snapshot discover() {
        return discover(registry.installed());
    }

    public Snapshot discover(List<InstalledExtension> extensions) {
        List<LoadedSource> sources = new ArrayList<>();
        Map<String, String> failures = new LinkedHashMap<>();
        List<ExtensionClassLoader> loaders = new ArrayList<>();
        for (InstalledExtension extension : extensions) {
            try {
                ExtensionClassLoader loader = new ExtensionClassLoader(extension.archive());
                loaders.add(loader);
                load(extension.metadata(), loader, sources);
            } catch (LinkageError | ReflectiveOperationException | IOException | RuntimeException failure) {
                failures.put(extension.metadata().packageName(), conciseMessage(failure));
            }
        }
        return new Snapshot(sources, failures, loaders);
    }

    private static void load(
            ExtensionApkMetadata metadata,
            ClassLoader loader,
            List<LoadedSource> sources) throws ReflectiveOperationException {
        List<Object> instances = new ArrayList<>();
        if (metadata.factoryClass().isPresent()) {
            expandFactory(instantiate(loader.loadClass(metadata.factoryClass().orElseThrow())), instances, true);
        }
        for (String className : metadata.sourceClasses()) {
            Object entryPoint = instantiate(loader.loadClass(className));
            expandFactory(entryPoint, instances, false);
        }
        if (instances.isEmpty()) {
            throw new IllegalStateException("Extension produced no sources");
        }
        for (Object instance : instances) {
            sources.add(describe(metadata, Objects.requireNonNull(instance, "source instance")));
        }
    }

    private static void expandFactory(Object entryPoint, List<Object> instances, boolean required)
            throws ReflectiveOperationException {
        Method createSources;
        try {
            createSources = entryPoint.getClass().getMethod("createSources");
        } catch (NoSuchMethodException exception) {
            if (required) {
                throw exception;
            }
            instances.add(entryPoint);
            return;
        }
        Object result = createSources.invoke(entryPoint);
        if (!(result instanceof Collection<?> collection)) {
            throw new IllegalStateException("Extension factory did not return a source collection");
        }
        instances.addAll(collection);
    }

    private static Object instantiate(Class<?> type) throws ReflectiveOperationException {
        return type.getConstructor().newInstance();
    }

    private static LoadedSource describe(ExtensionApkMetadata metadata, Object source)
            throws ReflectiveOperationException {
        long id = number(source, "getId").longValue();
        String name = text(source, "getName");
        String language = optionalText(source, "getLang", "und");
        return new LoadedSource(id, name, language, metadata.packageName(), metadata.kind(), source);
    }

    private static Number number(Object source, String method) throws ReflectiveOperationException {
        Object value = invoke(source, method);
        if (value instanceof Number number) {
            return number;
        }
        throw new IllegalStateException("Source " + method + " did not return a number");
    }

    private static String text(Object source, String method) throws ReflectiveOperationException {
        Object value = invoke(source, method);
        if (value instanceof String text && !text.isBlank()) {
            return text;
        }
        throw new IllegalStateException("Source " + method + " did not return text");
    }

    private static String optionalText(Object source, String method, String fallback)
            throws ReflectiveOperationException {
        try {
            Object value = invoke(source, method);
            return value instanceof String text && !text.isBlank() ? text : fallback;
        } catch (NoSuchMethodException ignored) {
            return fallback;
        }
    }

    private static Object invoke(Object source, String method) throws ReflectiveOperationException {
        Method operation = source.getClass().getMethod(method);
        try {
            return operation.invoke(source);
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException runtime) {
                throw runtime;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw exception;
        }
    }

    private static String conciseMessage(Throwable failure) {
        Throwable current = failure;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        String message = current.getMessage();
        return current.getClass().getSimpleName() + (message == null || message.isBlank() ? "" : ": " + message);
    }

    public static final class Snapshot implements AutoCloseable {
        private final List<LoadedSource> sources;
        private final Map<String, String> failures;
        private final List<ExtensionClassLoader> loaders;

        private Snapshot(
                List<LoadedSource> sources,
                Map<String, String> failures,
                List<ExtensionClassLoader> loaders) {
            this.sources = List.copyOf(sources);
            this.failures = Map.copyOf(failures);
            this.loaders = List.copyOf(loaders);
        }

        public List<LoadedSource> sources() {
            return sources;
        }

        public Map<String, String> failures() {
            return failures;
        }

        @Override
        public void close() {
            IOException failure = null;
            for (ExtensionClassLoader loader : loaders) {
                try {
                    loader.close();
                } catch (IOException exception) {
                    if (failure == null) {
                        failure = exception;
                    } else {
                        failure.addSuppressed(exception);
                    }
                }
            }
            if (failure != null) {
                throw new UncheckedIOException("Unable to close extension classloaders", failure);
            }
        }
    }
}
