package fr.vriege.anilib.tooling.extensionportability;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public final class ExtensionPortability {
    private static final int MAX_FILES = 20_000;
    private static final long MAX_FILE_BYTES = 2L * 1024 * 1024;
    private static final Pattern PACKAGE_IDENTITY = Pattern.compile(
            "(?:applicationId|namespace|package)\\s*(?:=)?\\s*[\\\"']([^\\\"']+)[\\\"']");
    private static final Pattern SOURCE_ID = Pattern.compile(
            "\\b(?:override\\s+)?(?:val|var|long|Long)?\\s*id\\s*(?::[^=]+)?=\\s*([+-]?[0-9]+)[lL]?\\b");
    private static final Set<String> TEXT_EXTENSIONS = Set.of(
            ".java", ".kt", ".kts", ".gradle", ".xml", ".properties", ".toml");
    private static final List<Detector> DETECTORS = List.of(
            detector("ANDROID_SDK", PortabilitySeverity.ADAPTATION_REQUIRED,
                    "Android SDK types require the isolated Desktop compatibility host", "\\b(?:android|androidx)\\."),
            detector("ANIYOMI_HOST_ABI", PortabilitySeverity.ADAPTATION_REQUIRED,
                    "Aniyomi host ABI calls need explicit Anilib Source adapters", "\\b(?:eu\\.kanade|tachiyomi)\\b"),
            detector("COMPILE_ONLY_HOST_DEPENDENCY", PortabilitySeverity.ADAPTATION_REQUIRED,
                    "Host-provided compileOnly libraries must be replaced or modelled", "\\bcompileOnly\\b"),
            detector("NATIVE_CODE", PortabilitySeverity.BLOCKED,
                    "Native loading needs a platform-specific implementation", "\\bSystem\\.load(?:Library)?\\s*\\("),
            detector("WEBVIEW", PortabilitySeverity.ADAPTATION_REQUIRED,
                    "WebView flows need the Anilib platform browser capability", "\\bWebView\\b"),
            detector("PREFERENCES", PortabilitySeverity.REVIEW,
                    "Preferences need portable Anilib definitions and storage",
                    "\\b(?:SharedPreferences|PreferenceScreen)\\b"),
            detector("TORRENT", PortabilitySeverity.ADAPTATION_REQUIRED,
                    "Torrent support needs an explicit platform capability", "(?i)\\b(?:libtorrent|torrent)\\b"),
            detector("DIRECT_NETWORK", PortabilitySeverity.REVIEW,
                    "Direct networking should use the constrained Anilib source context",
                    "\\b(?:okhttp3|java\\.net|ktor\\.client)\\b"),
            detector("DIRECT_STORAGE", PortabilitySeverity.ADAPTATION_REQUIRED,
                    "Direct storage access must move behind an Anilib capability",
                    "\\b(?:java\\.io|java\\.nio\\.file|android\\.os\\.Environment)\\b"));

    private ExtensionPortability() {
    }

    public static PortabilityReport analyze(Path sourceRepository) {
        return analyze(sourceRepository, Optional.empty(), List.of());
    }

    public static PortabilityReport analyze(
            Path sourceRepository,
            Optional<String> packageOverride,
            List<String> sourceIdOverrides) {
        Path root = requireRepository(sourceRepository);
        Optional<String> packageIdentity = normalize(packageOverride);
        Set<String> sourceIds = new LinkedHashSet<>(normalizeIds(sourceIdOverrides));
        List<PortabilityFinding> findings = new ArrayList<>();
        int inspected = 0;
        try (Stream<Path> paths = Files.walk(root)) {
            List<Path> files = paths
                    .filter(Files::isRegularFile)
                    .filter(path -> !ignored(root.relativize(path)))
                    .sorted()
                    .limit(MAX_FILES + 1L)
                    .toList();
            if (files.size() > MAX_FILES) {
                throw new IllegalArgumentException("Source repository exceeds " + MAX_FILES + " files");
            }
            for (Path file : files) {
                Path relative = root.relativize(file);
                if (nativeFile(relative)) {
                    findings.add(new PortabilityFinding(
                            "NATIVE_CODE",
                            PortabilitySeverity.BLOCKED,
                            relative,
                            1,
                            "Native binary or source file needs a platform-specific implementation"));
                }
                if (!textFile(file) || Files.size(file) > MAX_FILE_BYTES) {
                    continue;
                }
                inspected++;
                List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
                for (int index = 0; index < lines.size(); index++) {
                    String line = lines.get(index);
                    if (packageIdentity.isEmpty()) {
                        packageIdentity = identity(line);
                    }
                    sourceIds.addAll(sourceIds(line));
                    for (Detector detector : DETECTORS) {
                        if (detector.pattern().matcher(line).find()) {
                            findings.add(new PortabilityFinding(
                                    detector.category(),
                                    detector.severity(),
                                    relative,
                                    index + 1,
                                    detector.detail()));
                        }
                    }
                }
            }
        } catch (IOException exception) {
            throw new UncheckedIOException("Unable to inspect extension repository", exception);
        }
        findings.sort(Comparator.comparing((PortabilityFinding finding) -> finding.path().toString())
                .thenComparingInt(PortabilityFinding::line)
                .thenComparing(PortabilityFinding::category));
        return new PortabilityReport(
                root,
                packageIdentity,
                sourceIds.stream().sorted().toList(),
                List.copyOf(findings),
                inspected);
    }

    public static void writeReports(PortabilityReport report, Path outputDirectory) {
        PortabilityReport value = Objects.requireNonNull(report, "report must not be null");
        Path output = outputDirectory.toAbsolutePath().normalize();
        write(output.resolve("portability-report.json"), json(value));
        write(output.resolve("portability-report.md"), markdown(value));
    }

    public static void scaffold(
            PortabilityReport report,
            Path outputDirectory,
            String contentKind,
            String language) {
        PortabilityScaffolder.generate(report, outputDirectory, contentKind, language);
    }

    private static Optional<String> identity(String line) {
        Matcher matcher = PACKAGE_IDENTITY.matcher(line);
        if (!matcher.find()) {
            return Optional.empty();
        }
        String value = matcher.group(1).strip();
        return value.contains("extension") ? Optional.of(value) : Optional.empty();
    }

    private static List<String> sourceIds(String line) {
        Matcher matcher = SOURCE_ID.matcher(line);
        List<String> values = new ArrayList<>();
        while (matcher.find()) {
            values.add(matcher.group(1));
        }
        return values;
    }

    private static List<String> normalizeIds(List<String> values) {
        Objects.requireNonNull(values, "sourceIdOverrides must not be null");
        return values.stream().map(String::strip).filter(value -> !value.isEmpty()).distinct().toList();
    }

    private static Optional<String> normalize(Optional<String> value) {
        return Objects.requireNonNull(value, "packageOverride must not be null")
                .map(String::strip)
                .filter(text -> !text.isEmpty());
    }

    private static Path requireRepository(Path value) {
        Path root = Objects.requireNonNull(value, "sourceRepository must not be null")
                .toAbsolutePath()
                .normalize();
        if (!Files.isDirectory(root)) {
            throw new IllegalArgumentException("Source repository is not a directory: " + root);
        }
        return root;
    }

    private static boolean ignored(Path relative) {
        for (Path part : relative) {
            String name = part.toString();
            if (name.equals(".git") || name.equals(".gradle") || name.equals("build")
                    || name.equals("node_modules")) {
                return true;
            }
        }
        return false;
    }

    private static boolean textFile(Path file) {
        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        return TEXT_EXTENSIONS.stream().anyMatch(name::endsWith);
    }

    private static boolean nativeFile(Path file) {
        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".so") || name.endsWith(".dll") || name.endsWith(".dylib")
                || name.endsWith(".c") || name.endsWith(".cc") || name.endsWith(".cpp")
                || name.endsWith(".h") || name.endsWith(".hpp");
    }

    private static Detector detector(
            String category,
            PortabilitySeverity severity,
            String detail,
            String pattern) {
        return new Detector(category, severity, detail, Pattern.compile(pattern));
    }

    private static String json(PortabilityReport report) {
        StringBuilder result = new StringBuilder("{\n");
        result.append("  \"sourceRepository\": ").append(quote(report.sourceRepository().toString())).append(",\n");
        result.append("  \"packageIdentity\": ")
                .append(report.packageIdentity().map(ExtensionPortability::quote).orElse("null")).append(",\n");
        result.append("  \"sourceIds\": [");
        appendStrings(result, report.sourceIds());
        result.append("],\n  \"inspectedFiles\": ").append(report.inspectedFiles()).append(",\n");
        result.append("  \"hasBlockers\": ").append(report.hasBlockers()).append(",\n");
        result.append("  \"requiresAdaptation\": ").append(report.requiresAdaptation()).append(",\n");
        result.append("  \"findings\": [");
        for (int index = 0; index < report.findings().size(); index++) {
            PortabilityFinding finding = report.findings().get(index);
            if (index > 0) {
                result.append(',');
            }
            result.append("\n    {\"category\":").append(quote(finding.category()))
                    .append(",\"severity\":").append(quote(finding.severity().name()))
                    .append(",\"path\":").append(quote(finding.path().toString().replace('\\', '/')))
                    .append(",\"line\":").append(finding.line())
                    .append(",\"detail\":").append(quote(finding.detail())).append('}');
        }
        if (!report.findings().isEmpty()) {
            result.append('\n');
        }
        return result.append("  ]\n}\n").toString();
    }

    private static String markdown(PortabilityReport report) {
        StringBuilder result = new StringBuilder("# Extension portability report\n\n");
        result.append("- Package identity: `")
                .append(report.packageIdentity().orElse("not detected"))
                .append("`\n- Source IDs: ")
                .append(report.sourceIds().isEmpty() ? "not detected" : String.join(", ", report.sourceIds()))
                .append("\n- Inspected text files: ").append(report.inspectedFiles())
                .append("\n- Status: ")
                .append(report.hasBlockers()
                        ? "blocked"
                        : report.requiresAdaptation() ? "adaptation required" : "review")
                .append("\n\n## Findings\n\n");
        if (report.findings().isEmpty()) {
            return result.append("No known portability risks were detected. Manual review is still required.\n")
                    .toString();
        }
        for (PortabilityFinding finding : report.findings()) {
            result.append("- **").append(finding.severity()).append(" / ")
                    .append(finding.category()).append("** — `")
                    .append(finding.path().toString().replace('\\', '/')).append(':').append(finding.line())
                    .append("`: ").append(finding.detail()).append('\n');
        }
        return result.toString();
    }

    private static void appendStrings(StringBuilder result, List<String> values) {
        for (int index = 0; index < values.size(); index++) {
            if (index > 0) {
                result.append(',');
            }
            result.append(quote(values.get(index)));
        }
    }

    private static String quote(String value) {
        StringBuilder result = new StringBuilder("\"");
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            switch (character) {
                case '\\' -> result.append("\\\\");
                case '"' -> result.append("\\\"");
                case '\n' -> result.append("\\n");
                case '\r' -> result.append("\\r");
                case '\t' -> result.append("\\t");
                default -> result.append(character);
            }
        }
        return result.append('"').toString();
    }

    private static void write(Path file, String content) {
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, content, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new UncheckedIOException("Unable to write portability output " + file, exception);
        }
    }

    static String packageHash(String packageIdentity) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(packageIdentity.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, 8);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JDK 21 does not provide SHA-256", exception);
        }
    }

    private record Detector(
            String category,
            PortabilitySeverity severity,
            String detail,
            Pattern pattern) {
    }
}
