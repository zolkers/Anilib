package fr.vriege.anilib.tooling.extensionportability;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Stream;

final class PortabilityScaffolder {
    private static final String IMPORT_KEYWORD = "import";
    private static final String SOURCE_API = "fr.vriege.anilib.feature.source";

    private PortabilityScaffolder() {
    }

    static void generate(
            PortabilityReport report,
            Path outputDirectory,
            String contentKind,
            String language) {
        PortabilityReport value = Objects.requireNonNull(report, "report must not be null");
        String packageIdentity = value.packageIdentity()
                .orElseThrow(() -> new IllegalArgumentException("A package identity is required to scaffold"));
        if (value.sourceIds().isEmpty()) {
            throw new IllegalArgumentException("At least one source ID is required to scaffold");
        }
        String kind = kind(contentKind);
        String languageTag = text(language, "language").toLowerCase(Locale.ROOT);
        String hash = ExtensionPortability.packageHash(packageIdentity);
        String javaPackage = "fr.vriege.anilib.extension.ported.p" + hash;
        String moduleName = javaPackage;
        Path output = Objects.requireNonNull(outputDirectory, "outputDirectory must not be null")
                .toAbsolutePath()
                .normalize();
        ensureEmpty(output);
        Path javaDirectory = output.resolve("src/main/java").resolve(javaPackage.replace('.', '/'));
        Path resources = output.resolve("src/main/resources/META-INF");
        write(output.resolve("build.gradle"), buildFile(hash));
        write(output.resolve("module.properties"), moduleProperties(hash));
        write(output.resolve("src/main/java/module-info.java"), moduleInfo(moduleName, javaPackage));
        write(resources.resolve("anilib-extension.properties"),
                descriptor(packageIdentity, javaPackage, value.sourceIds()));
        write(output.resolve("source-publisher.properties"),
                publisher(packageIdentity, hash, javaPackage, languageTag, kind, value.sourceIds()));
        for (int index = 0; index < value.sourceIds().size(); index++) {
            write(javaDirectory.resolve("PortedSourceFactory" + index + ".java"),
                    factory(javaPackage, index));
            write(javaDirectory.resolve("PortedSource" + index + ".java"),
                    source(javaPackage, index, value.sourceIds().get(index), languageTag, kind));
        }
    }

    private static String buildFile(String hash) {
        return """
                dependencies {
                    implementation project(':Anilib:Features:Source:Api')
                }

                tasks.named('jar') {
                    archiveFileName = 'anilib-ported-%s.jar'
                }
                """.formatted(hash);
    }

    private static String moduleProperties(String hash) {
        return """
                id=extension.ported.%s
                layer=EXTENSION
                role=BUNDLE
                owner=extension.ported.%s
                dependencies=feature.source.api
                """.formatted(hash, hash);
    }

    private static String moduleInfo(String moduleName, String javaPackage) {
        return """
                module %s {
                    requires transitive fr.vriege.anilib.feature.source.api;

                    exports %s;
                }
                """.formatted(moduleName, javaPackage);
    }

    private static String descriptor(
            String packageIdentity,
            String javaPackage,
            List<String> sourceIds) {
        StringBuilder result = new StringBuilder()
                .append("package=").append(property(packageIdentity)).append('\n')
                .append("versionCode=1\n")
                .append("api=1.6\n")
                .append("module=").append(javaPackage).append('\n')
                .append("source.count=").append(sourceIds.size()).append('\n');
        for (int index = 0; index < sourceIds.size(); index++) {
            String prefix = "source." + index + ".";
            result.append(prefix).append("id=").append(property(sourceIds.get(index))).append('\n')
                    .append(prefix).append("component=extension.ported.").append(index).append('\n')
                    .append(prefix).append("name=Ported source ").append(property(sourceIds.get(index))).append('\n')
                    .append(prefix).append("factory=").append(javaPackage)
                    .append(".PortedSourceFactory").append(index).append('\n')
                    .append(prefix).append("origins=\n");
        }
        return result.toString();
    }

    private static String publisher(
            String packageIdentity,
            String hash,
            String javaPackage,
            String language,
            String kind,
            List<String> sourceIds) {
        StringBuilder result = new StringBuilder()
                .append("bundle=build/libs/anilib-ported-").append(hash).append(".jar\n")
                .append("name=Ported extension ").append(hash).append('\n')
                .append("package=").append(property(packageIdentity)).append('\n')
                .append("lang=").append(property(language)).append('\n')
                .append("versionCode=1\nversion=0.1.0\nadult=false\n")
                .append("kind=").append(kind).append("\napi=1.6\n")
                .append("keyId=replace-with-publisher-key-id\n")
                .append("source.count=").append(sourceIds.size()).append('\n');
        for (int index = 0; index < sourceIds.size(); index++) {
            String prefix = "source." + index + ".";
            result.append(prefix).append("id=").append(property(sourceIds.get(index))).append('\n')
                    .append(prefix).append("name=Ported source ").append(property(sourceIds.get(index))).append('\n')
                    .append(prefix).append("lang=").append(property(language)).append('\n')
                    .append(prefix).append("baseUrl=\n")
                    .append(prefix).append("factory=").append(javaPackage)
                    .append(".PortedSourceFactory").append(index).append('\n');
        }
        return result.toString();
    }

    private static String factory(String javaPackage, int index) {
        return """
                package %1$s;

                %2$s %3$s.Source;
                %2$s %3$s.SourceExtensionContext;
                %2$s %3$s.SourceExtensionFactory;

                public final class PortedSourceFactory%4$d implements SourceExtensionFactory {
                    public PortedSourceFactory%4$d() {
                    }

                    @Override
                    public Source create(SourceExtensionContext context) {
                        return new PortedSource%4$d();
                    }
                }
                """.formatted(javaPackage, IMPORT_KEYWORD, SOURCE_API, index);
    }

    private static String source(
            String javaPackage,
            int index,
            String sourceId,
            String language,
            String kind) {
        return """
                package %1$s;

                %2$s %3$s.CatalogueSource;
                %2$s %3$s.SourceApiVersion;
                %2$s %3$s.SourceBrowseRequest;
                %2$s %3$s.SourceContentKind;
                %2$s %3$s.SourceDescriptor;
                %2$s %3$s.SourceId;
                %2$s %3$s.SourcePage;
                %2$s %3$s.SourceSearchRequest;

                %2$s java.util.List;
                %2$s java.util.Set;

                final class PortedSource%4$d implements CatalogueSource {
                    private static final SourceId ID = SourceId.of("%5$s");

                    @Override
                    public SourceDescriptor descriptor() {
                        return new SourceDescriptor(
                                ID,
                                "Ported source %5$s",
                                "0.1.0",
                                "%6$s",
                                Set.of(SourceContentKind.%7$s),
                                new SourceApiVersion(1, 6));
                    }

                    @Override
                    public SourcePage popular(SourceBrowseRequest request) {
                        return new SourcePage(List.of(), false);
                    }

                    @Override
                    public SourcePage search(SourceSearchRequest request) {
                        return new SourcePage(List.of(), false);
                    }
                }
                """.formatted(
                javaPackage,
                IMPORT_KEYWORD,
                SOURCE_API,
                index,
                javaString(sourceId),
                javaString(language),
                kind.toUpperCase(Locale.ROOT));
    }

    private static void ensureEmpty(Path output) {
        if (!Files.exists(output)) {
            return;
        }
        if (!Files.isDirectory(output)) {
            throw new IllegalArgumentException("Scaffold output is not a directory: " + output);
        }
        try (Stream<Path> entries = Files.list(output)) {
            if (entries.findAny().isPresent()) {
                throw new IllegalArgumentException("Scaffold output must be empty: " + output);
            }
        } catch (IOException exception) {
            throw new UncheckedIOException("Unable to inspect scaffold output", exception);
        }
    }

    private static String kind(String value) {
        String normalized = text(value, "contentKind").toLowerCase(Locale.ROOT);
        if (!normalized.equals("anime") && !normalized.equals("manga")) {
            throw new IllegalArgumentException("contentKind must be anime or manga");
        }
        return normalized;
    }

    private static String text(String value, String name) {
        String result = Objects.requireNonNull(value, name + " must not be null").strip();
        if (result.isEmpty()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return result;
    }

    private static String property(String value) {
        return value.replace("\\", "\\\\").replace("\n", "\\n").replace("\r", "\\r");
    }

    private static String javaString(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r");
    }

    private static void write(Path file, String content) {
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, content, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new UncheckedIOException("Unable to write scaffold file " + file, exception);
        }
    }
}
