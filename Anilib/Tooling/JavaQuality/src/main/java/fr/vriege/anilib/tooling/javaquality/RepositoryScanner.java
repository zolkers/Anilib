package fr.vriege.anilib.tooling.javaquality;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Properties;
import java.util.stream.Stream;

public final class RepositoryScanner {
    public RepositoryScanner() {
    }

    public RepositorySnapshot scan(Path root) {
        Path normalizedRoot = root.toAbsolutePath().normalize();
        Path sourceRoot = normalizedRoot.resolve("Anilib");
        if (!Files.isDirectory(sourceRoot)) {
            throw new IllegalArgumentException("Anilib directory not found under " + normalizedRoot);
        }

        try {
            List<Path> files = listFiles(sourceRoot);
            List<ModuleMetadata> modules = readModules(normalizedRoot, files);
            List<JavaSource> sources = readSources(normalizedRoot, files, modules);
            List<KotlinSource> kotlinSources = readKotlinSources(normalizedRoot, files, modules);
            List<Path> buildFiles = listFiles(normalizedRoot).stream()
                    .filter(path -> path.getFileName().toString().equals("build.gradle"))
                    .map(normalizedRoot::relativize)
                    .sorted()
                    .toList();
            return new RepositorySnapshot(normalizedRoot, modules, sources, kotlinSources, buildFiles);
        } catch (IOException exception) {
            throw new UncheckedIOException("Unable to index Anilib repository", exception);
        }
    }

    private static List<Path> listFiles(Path root) throws IOException {
        try (Stream<Path> paths = Files.walk(root)) {
            return paths.filter(Files::isRegularFile)
                    .filter(path -> !containsSegment(path, "build"))
                    .filter(path -> !containsSegment(path, ".gradle"))
                    .sorted()
                    .toList();
        }
    }

    private static boolean containsSegment(Path path, String segment) {
        for (Path part : path) {
            if (part.toString().equals(segment)) {
                return true;
            }
        }
        return false;
    }

    private static List<ModuleMetadata> readModules(Path root, List<Path> files) throws IOException {
        List<ModuleMetadata> modules = new ArrayList<>();
        for (Path path : files) {
            if (!path.getFileName().toString().equals("module.properties")) {
                continue;
            }
            Properties properties = new Properties();
            try (java.io.Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                properties.load(reader);
            }
            List<String> dependencies = split(properties.getProperty("dependencies", ""));
            modules.add(new ModuleMetadata(
                    required(properties, "id", path),
                    ModuleMetadata.Layer.valueOf(required(properties, "layer", path)),
                    required(properties, "role", path),
                    required(properties, "owner", path),
                    ModuleMetadata.Language.valueOf(properties.getProperty("language", "JAVA").trim()),
                    dependencies,
                    path.getParent(),
                    root.relativize(path)));
        }
        modules.sort(Comparator.comparing(ModuleMetadata::id));
        return List.copyOf(modules);
    }

    private static List<String> split(String value) {
        if (value.isBlank()) {
            return List.of();
        }
        return Stream.of(value.split(","))
                .map(String::trim)
                .filter(part -> !part.isEmpty())
                .sorted()
                .toList();
    }

    private static String required(Properties properties, String key, Path path) {
        String value = properties.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(path + " is missing property " + key);
        }
        return value.trim();
    }

    private static List<JavaSource> readSources(
            Path root,
            List<Path> files,
            List<ModuleMetadata> modules) throws IOException {
        List<JavaSource> sources = new ArrayList<>();
        for (Path path : files) {
            if (!path.getFileName().toString().endsWith(".java")) {
                continue;
            }
            ModuleMetadata owner = modules.stream()
                    .filter(module -> path.startsWith(module.directory()))
                    .max(Comparator.comparingInt(module -> module.directory().getNameCount()))
                    .orElseThrow(() -> new IllegalArgumentException("No module.properties owns " + path));
            sources.add(new JavaSource(
                    root.relativize(path),
                    path,
                    owner,
                    Files.readAllLines(path, StandardCharsets.UTF_8)));
        }
        sources.sort(Comparator.comparing(source -> source.path().toString()));
        return List.copyOf(sources);
    }

    private static List<KotlinSource> readKotlinSources(
            Path root,
            List<Path> files,
            List<ModuleMetadata> modules) throws IOException {
        List<KotlinSource> sources = new ArrayList<>();
        for (Path path : files) {
            if (!path.getFileName().toString().endsWith(".kt")) {
                continue;
            }
            ModuleMetadata owner = modules.stream()
                    .filter(module -> path.startsWith(module.directory()))
                    .max(Comparator.comparingInt(module -> module.directory().getNameCount()))
                    .orElseThrow(() -> new IllegalArgumentException("No module.properties owns " + path));
            sources.add(new KotlinSource(
                    root.relativize(path),
                    path,
                    owner,
                    Files.readAllLines(path, StandardCharsets.UTF_8)));
        }
        sources.sort(Comparator.comparing(source -> source.path().toString()));
        return List.copyOf(sources);
    }
}
