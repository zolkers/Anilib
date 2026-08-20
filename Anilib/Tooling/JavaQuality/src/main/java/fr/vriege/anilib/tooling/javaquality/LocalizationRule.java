package fr.vriege.anilib.tooling.javaquality;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class LocalizationRule implements AnilibJavaRule {
    private static final Path UI_ROOT = Path.of(
            "Anilib", "Platforms", "Compose", "src", "shared", "kotlin");
    private static final Path PLATFORM_RESOURCES = Path.of(
            "Anilib", "Platforms", "Compose", "src", "shared", "resources",
            "META-INF", "anilib", "i18n", "platform-compose");
    private static final Path FEATURES_ROOT = Path.of("Anilib", "Features");
    private static final String EXPRESSION =
            "\"(?:\\\\.|[^\"\\\\])*\"(?:\\s*\\+\\s*\"(?:\\\\.|[^\"\\\\])*\")*";
    private static final Pattern UI_TEXT = Pattern.compile(
            "(?:Text|SettingsRow|SettingsSection|SettingsHint|SettingsSwitchRow|SummaryCard|MoreScaffold"
                    + "|EmptyState|ErrorState|EmptyPage|AnilibSection|MoreRow|MoreSwitchRow|SectionTitle"
                    + "|StatisticsHeading|TrackerSectionHeader|MediaArtworkPlaceholder)"
                    + "\\(\\s*(?<expression>" + EXPRESSION + ")",
            Pattern.DOTALL);
    private static final Pattern NAMED_UI_TEXT = Pattern.compile(
            "Text\\(\\s*text\\s*=\\s*(?<expression>" + EXPRESSION + ")",
            Pattern.DOTALL);
    private static final Pattern CONTENT_DESCRIPTION = Pattern.compile(
            "contentDescription\\s*=\\s*(?<expression>" + EXPRESSION + ")",
            Pattern.DOTALL);
    private static final Pattern SETTINGS_DESCRIPTION = Pattern.compile(
            "(?:SettingsRow|SettingsSwitchRow|MoreRow|MoreSwitchRow|SummaryCard)\\(\\s*" + EXPRESSION
                    + "\\s*,\\s*(?<expression>" + EXPRESSION + ")",
            Pattern.DOTALL);
    private static final Pattern TRANSLATE_CALL = Pattern.compile(
            "UiTranslations\\.translate\\(\\s*(?<expression>" + EXPRESSION + ")",
            Pattern.DOTALL);
    private static final Pattern FORMAT_CALL = Pattern.compile(
            "UiTranslations\\.format\\(\\s*(?<expression>" + EXPRESSION + ")",
            Pattern.DOTALL);
    private static final Pattern STRING = Pattern.compile("\"((?:\\\\.|[^\"\\\\])*)\"");
    private static final Pattern KOTLIN_TEMPLATE = Pattern.compile(
            "\\$\\{[^}]*}|\\$[A-Za-z_][A-Za-z0-9_]*");
    private static final Pattern NATURAL_LANGUAGE = Pattern.compile("[A-Za-z]{2,}");
    private static final Pattern TRANSLATION_KEY = Pattern.compile("[a-z][a-z0-9_]*(?:\\.[a-z0-9_]+)+");
    private static final Set<String> UNIVERSAL = Set.of(
            "", "-50", "+50", "↑", "↓", "·", "Anilib", "PiP", "SHA-256");

    public LocalizationRule() {
    }

    @Override
    public String name() {
        return "localization";
    }

    @Override
    public List<Diagnostic> analyze(RepositorySnapshot repository) {
        Path root = repository.root().resolve(UI_ROOT);
        Path catalog = root.resolve(Path.of(
                "fr", "vriege", "anilib", "platform", "compose", "UiTranslations.kt"));
        if (!Files.isRegularFile(catalog) || Files.isSymbolicLink(catalog)) {
            return List.of(new Diagnostic(name(), UI_ROOT, 1,
                    "Shared UI translation catalog is missing or symbolic"));
        }
        try {
            List<Diagnostic> diagnostics = new ArrayList<>();
            Set<String> translations = readCatalogs(repository.root(), catalog, diagnostics);
            try (var paths = Files.walk(root)) {
                for (Path file : paths.filter(path -> path.toString().endsWith(".kt")).toList()) {
                    inspect(repository.root(), file, translations, diagnostics);
                }
            }
            return List.copyOf(diagnostics);
        } catch (IOException exception) {
            throw new UncheckedIOException("Unable to inspect shared UI localization", exception);
        }
    }

    private Set<String> readCatalogs(
            Path repository,
            Path platformCatalog,
            List<Diagnostic> diagnostics) throws IOException {
        String platformSource = Files.readString(platformCatalog, StandardCharsets.UTF_8);
        Set<String> translations = new HashSet<>();
        rejectHardcodedCatalog(repository.relativize(platformCatalog), platformSource, diagnostics);
        appendResourcePair(
                repository,
                repository.resolve(PLATFORM_RESOURCES),
                translations,
                diagnostics);
        Path features = repository.resolve(FEATURES_ROOT);
        if (!Files.isDirectory(features) || Files.isSymbolicLink(features)) {
            return Set.copyOf(translations);
        }
        try (var featurePaths = Files.list(features)) {
            for (Path feature : featurePaths.filter(Files::isDirectory).toList()) {
                Path uiSources = feature.resolve(Path.of("Ui", "src", "main", "java"));
                if (!Files.isDirectory(uiSources) || Files.isSymbolicLink(uiSources)) {
                    continue;
                }
                List<Path> catalogs;
                try (var files = Files.walk(uiSources)) {
                    catalogs = files
                            .filter(path -> path.getFileName().toString().endsWith("TranslationCatalog.java"))
                            .toList();
                }
                if (catalogs.size() != 1) {
                    diagnostics.add(new Diagnostic(name(), repository.relativize(uiSources), 1,
                            "Every feature UI must provide exactly one translation catalog"));
                    continue;
                }
                Path featureCatalog = catalogs.getFirst();
                String catalogSource = Files.readString(featureCatalog, StandardCharsets.UTF_8);
                rejectHardcodedCatalog(repository.relativize(featureCatalog), catalogSource, diagnostics);
                if (!catalogSource.contains("TranslationCatalog.resources(")) {
                    diagnostics.add(new Diagnostic(name(), repository.relativize(featureCatalog), 1,
                            "Feature translation catalogs must load keyed classpath resources"));
                }
                appendResourceCatalogs(repository, feature, translations, diagnostics);
            }
        }
        return Set.copyOf(translations);
    }

    private static void appendResourceCatalogs(
            Path repository,
            Path feature,
            Set<String> translations,
            List<Diagnostic> diagnostics) throws IOException {
        Path resources = feature.resolve(Path.of("Ui", "src", "main", "resources"));
        if (!Files.isDirectory(resources) || Files.isSymbolicLink(resources)) {
            diagnostics.add(new Diagnostic("localization", repository.relativize(feature), 1,
                    "Every feature UI must provide keyed en.properties and fr.properties resources"));
            return;
        }
        List<Path> englishFiles;
        try (var paths = Files.walk(resources)) {
            englishFiles = paths
                    .filter(path -> path.getFileName().toString().equals("en.properties"))
                    .toList();
        }
        if (englishFiles.size() != 1) {
            diagnostics.add(new Diagnostic("localization", repository.relativize(resources), 1,
                    "Every feature UI must provide exactly one English/French resource catalog"));
            return;
        }
        appendResourcePair(repository, englishFiles.getFirst().getParent(), translations, diagnostics);
    }

    private static void appendResourcePair(
            Path repository,
            Path catalogDirectory,
            Set<String> translations,
            List<Diagnostic> diagnostics) throws IOException {
        Path englishFile = catalogDirectory.resolve("en.properties");
        if (!Files.isRegularFile(englishFile) || Files.isSymbolicLink(englishFile)) {
            diagnostics.add(new Diagnostic("localization", repository.relativize(catalogDirectory), 1,
                    "Resource catalog must provide a regular en.properties file"));
            return;
        }
        requireMatchingFrenchKeys(repository, englishFile, diagnostics);
        Path frenchFile = englishFile.resolveSibling("fr.properties");
        appendMessages(translations, properties(englishFile));
        if (Files.isRegularFile(frenchFile) && !Files.isSymbolicLink(frenchFile)) {
            appendMessages(translations, properties(frenchFile));
        }
    }

    private static void appendMessages(Set<String> translations, Properties properties) {
        properties.stringPropertyNames().forEach(key -> {
            translations.add(key);
            translations.add(properties.getProperty(key));
        });
    }

    private static void requireMatchingFrenchKeys(
            Path repository,
            Path englishFile,
            List<Diagnostic> diagnostics) throws IOException {
        Path frenchFile = englishFile.resolveSibling("fr.properties");
        if (!Files.isRegularFile(frenchFile) || Files.isSymbolicLink(frenchFile)) {
            diagnostics.add(new Diagnostic("localization", repository.relativize(englishFile), 1,
                    "Resource catalog must provide a regular fr.properties sibling"));
            return;
        }
        Properties english = properties(englishFile);
        Properties french = properties(frenchFile);
        requireUniqueKeys(repository, englishFile, diagnostics);
        requireUniqueKeys(repository, frenchFile, diagnostics);
        if (!english.stringPropertyNames().equals(french.stringPropertyNames())) {
            diagnostics.add(new Diagnostic("localization", repository.relativize(frenchFile), 1,
                    "English and French resource catalogs must contain identical keys"));
        }
        for (String key : english.stringPropertyNames()) {
            if (!TRANSLATION_KEY.matcher(key).matches()) {
                diagnostics.add(new Diagnostic("localization", repository.relativize(englishFile), 1,
                        "Translation keys must be semantic dotted identifiers: " + key));
            }
        }
    }

    private static void requireUniqueKeys(
            Path repository,
            Path file,
            List<Diagnostic> diagnostics) throws IOException {
        Set<String> keys = new HashSet<>();
        int line = 0;
        for (String sourceLine : Files.readAllLines(file, StandardCharsets.UTF_8)) {
            line++;
            String candidate = sourceLine.strip();
            if (candidate.isEmpty() || candidate.startsWith("#") || candidate.startsWith("!")) {
                continue;
            }
            int separator = candidate.indexOf('=');
            if (separator <= 0) {
                diagnostics.add(new Diagnostic("localization", repository.relativize(file), line,
                        "Translation resources must use key=value entries"));
                continue;
            }
            String key = candidate.substring(0, separator).strip();
            if (!keys.add(key)) {
                diagnostics.add(new Diagnostic("localization", repository.relativize(file), line,
                        "Duplicate translation key: " + key));
            }
        }
    }

    private static void rejectHardcodedCatalog(
            Path relative,
            String source,
            List<Diagnostic> diagnostics) {
        if (source.contains("TranslationCatalog.french(")
                || source.contains("Map.ofEntries(")
                || source.contains("Map.entry(")
                || source.contains("private val translations")
                || source.contains("translateFrenchDynamic")) {
            diagnostics.add(new Diagnostic("localization", relative, 1,
                    "Translation maps must live in keyed en.properties/fr.properties resources"));
        }
    }

    private static Properties properties(Path file) throws IOException {
        Properties properties = new Properties();
        try (var reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            properties.load(reader);
        }
        return properties;
    }

    private void inspect(
            Path repository,
            Path file,
            Set<String> translations,
            List<Diagnostic> diagnostics) throws IOException {
        String content = Files.readString(file, StandardCharsets.UTF_8);
        Path relative = repository.relativize(file);
        if (!file.endsWith("LocalizedText.kt") && (content.contains("import androidx.compose.material3.Text\n")
                || content.contains("import androidx.compose.material3.Icon\n"))) {
            diagnostics.add(new Diagnostic(name(), relative, 1,
                    "Shared UI must use the localization-aware Text and Icon adapters"));
        }
        requireCatalogEntries(relative, content, translations, UI_TEXT, diagnostics);
        requireCatalogEntries(relative, content, translations, NAMED_UI_TEXT, diagnostics);
        requireCatalogEntries(relative, content, translations, SETTINGS_DESCRIPTION, diagnostics);
        requireCatalogEntries(relative, content, translations, CONTENT_DESCRIPTION, diagnostics);
        requireCatalogEntries(relative, content, translations, TRANSLATE_CALL, diagnostics);
        requireCatalogEntries(relative, content, translations, FORMAT_CALL, diagnostics);
    }

    private void requireCatalogEntries(
            Path relative,
            String content,
            Set<String> translations,
            Pattern pattern,
            List<Diagnostic> diagnostics) {
        Matcher calls = pattern.matcher(content);
        while (calls.find()) {
            String value = join(calls.group("expression"));
            if (UNIVERSAL.contains(value)) {
                continue;
            }
            String evidence = value.substring(0, Math.min(48, value.length()));
            if (value.contains("$")) {
                String literal = KOTLIN_TEMPLATE.matcher(value).replaceAll("");
                if (NATURAL_LANGUAGE.matcher(literal).find()) {
                    diagnostics.add(new Diagnostic(name(), relative, line(content, calls.start()),
                            "Dynamic UI text must use a parameterized translation key: " + evidence));
                }
                continue;
            }
            if (!TRANSLATION_KEY.matcher(value).matches()) {
                diagnostics.add(new Diagnostic(name(), relative, line(content, calls.start()),
                        "Static UI text must reference a translation key: " + evidence));
                continue;
            }
            if (!translations.contains(value)) {
                diagnostics.add(new Diagnostic(name(), relative, line(content, calls.start()),
                        "English/French resources are missing for UI key: " + evidence));
            }
        }
    }

    private static String join(String expression) {
        StringBuilder value = new StringBuilder();
        Matcher strings = STRING.matcher(expression);
        while (strings.find()) {
            value.append(strings.group(1)
                    .replace("\\\"", "\"")
                    .replace("\\n", "\n")
                    .replace("\\\\", "\\"));
        }
        return value.toString();
    }

    private static int line(String content, int offset) {
        int line = 1;
        for (int index = 0; index < offset; index++) {
            if (content.charAt(index) == '\n') {
                line++;
            }
        }
        return line;
    }
}
