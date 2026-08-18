package fr.vriege.anilib.tooling.javaquality;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Enforces ownership, dependency boundaries, imports, and format for Kotlin UI adapters. */
public final class KotlinSourceRule implements AnilibJavaRule {
    private static final int MAX_LINE_LENGTH = 120;
    private static final String ROOT_PACKAGE = "fr.vriege.anilib";
    private static final Pattern IMPORT = Pattern.compile("^\\s*import\\s+([^ ]+)\\s*$");
    private static final Map<String, Set<String>> EXTERNAL_IMPORT_PREFIXES = Map.of(
            "platform.android", Set.of("android.", "androidx.activity.", "androidx.compose."),
            "platform.compose", Set.of(
                    "androidx.compose.",
                    "com.multiplatform.webview.",
                    "dev.datlag.kcef.",
                    "io.github.kdroidfilter.composemediaplayer.",
                    "kotlinx.coroutines."),
            "platform.desktop", Set.of("androidx.compose.", "org.jetbrains.compose.", "org.jetbrains.skia."));

    public KotlinSourceRule() {
    }

    @Override
    public String name() {
        return "kotlin-source";
    }

    @Override
    public List<Diagnostic> analyze(RepositorySnapshot repository) {
        List<Diagnostic> diagnostics = new ArrayList<>();
        List<PackageOwner> packageOwners = packageOwners(repository);
        for (KotlinSource source : repository.kotlinSources()) {
            validatePackage(source, diagnostics);
            validateLines(source, packageOwners, diagnostics);
        }
        return diagnostics;
    }

    private static List<PackageOwner> packageOwners(RepositorySnapshot repository) {
        List<PackageOwner> owners = new ArrayList<>();
        repository.javaSources().stream()
                .filter(source -> !source.isModuleDescriptor())
                .forEach(source -> source.packageName()
                        .ifPresent(name -> owners.add(new PackageOwner(name, source.module()))));
        repository.kotlinSources().forEach(source -> source.packageName()
                .ifPresent(name -> owners.add(new PackageOwner(name, source.module()))));
        return owners.stream()
                .distinct()
                .sorted(Comparator.comparingInt((PackageOwner owner) -> owner.packageName().length()).reversed())
                .toList();
    }

    private static void validatePackage(KotlinSource source, List<Diagnostic> diagnostics) {
        String packageName = source.packageName().orElse("");
        if (!packageName.startsWith(ROOT_PACKAGE)) {
            diagnostics.add(diagnostic(source, 1, "Kotlin package must start with " + ROOT_PACKAGE));
            return;
        }
        Path sourceRoot = source.absolutePath().getParent();
        int packageSegments = packageName.split("\\.").length;
        for (int index = 0; index < packageSegments; index++) {
            sourceRoot = sourceRoot.getParent();
        }
        Path expected = sourceRoot.resolve(packageName.replace('.', java.io.File.separatorChar))
                .resolve(source.absolutePath().getFileName())
                .normalize();
        if (!expected.equals(source.absolutePath())) {
            diagnostics.add(diagnostic(source, 1, "Kotlin package does not match the source directory"));
        }
    }

    private static void validateLines(
            KotlinSource source,
            List<PackageOwner> packageOwners,
            List<Diagnostic> diagnostics) {
        for (int index = 0; index < source.lines().size(); index++) {
            String line = source.lines().get(index);
            int lineNumber = index + 1;
            if (line.indexOf('\t') >= 0) {
                diagnostics.add(diagnostic(source, lineNumber, "Tabs are forbidden in Kotlin sources"));
            }
            if (!line.isEmpty() && Character.isWhitespace(line.charAt(line.length() - 1))) {
                diagnostics.add(diagnostic(source, lineNumber, "Trailing whitespace is forbidden"));
            }
            if (line.length() > MAX_LINE_LENGTH) {
                diagnostics.add(diagnostic(source, lineNumber,
                        "Line exceeds " + MAX_LINE_LENGTH + " characters"));
            }
            validateImport(source, packageOwners, lineNumber, line, diagnostics);
        }
    }

    private static void validateImport(
            KotlinSource source,
            List<PackageOwner> packageOwners,
            int lineNumber,
            String line,
            List<Diagnostic> diagnostics) {
        Matcher matcher = IMPORT.matcher(line);
        if (!matcher.matches()) {
            return;
        }
        String imported = matcher.group(1);
        if (imported.endsWith(".*")) {
            diagnostics.add(diagnostic(source, lineNumber, "Wildcard imports are forbidden"));
        }
        if (!allowedImport(source.module(), imported)) {
            diagnostics.add(diagnostic(source, lineNumber, "External Kotlin import is forbidden: " + imported));
        }
        validateAnilibDependency(source, packageOwners, lineNumber, imported, diagnostics);
    }

    private static void validateAnilibDependency(
            KotlinSource source,
            List<PackageOwner> packageOwners,
            int lineNumber,
            String imported,
            List<Diagnostic> diagnostics) {
        if (!imported.startsWith(ROOT_PACKAGE + ".")) {
            return;
        }
        for (PackageOwner owner : packageOwners) {
            if (!imported.startsWith(owner.packageName() + ".")) {
                continue;
            }
            String targetId = owner.module().id();
            if (!targetId.equals(source.module().id())
                    && !source.module().dependencies().contains(targetId)) {
                diagnostics.add(diagnostic(source, lineNumber,
                        "Kotlin import requires manifest dependency " + targetId));
            }
            return;
        }
    }

    private static boolean allowedImport(ModuleMetadata module, String imported) {
        if (imported.startsWith("java.")
                || imported.startsWith("javax.")
                || imported.startsWith("kotlin.")
                || imported.startsWith("fr.vriege.anilib.")) {
            return true;
        }
        return module.layer() == ModuleMetadata.Layer.PLATFORM
                && EXTERNAL_IMPORT_PREFIXES.getOrDefault(module.id(), Set.of()).stream()
                        .anyMatch(imported::startsWith);
    }

    private static Diagnostic diagnostic(KotlinSource source, int line, String message) {
        return new Diagnostic("kotlin-source", source.path(), line, message);
    }

    private record PackageOwner(String packageName, ModuleMetadata module) {
    }
}
