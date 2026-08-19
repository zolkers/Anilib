package fr.vriege.anilib.tooling.javaquality;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class LocalizationRule implements AnilibJavaRule {
    private static final Path UI_ROOT = Path.of(
            "Anilib", "Platforms", "Compose", "src", "shared", "kotlin");
    private static final Path FEATURES_ROOT = Path.of("Anilib", "Features");
    private static final String EXPRESSION =
            "\"(?:\\\\.|[^\"\\\\])*\"(?:\\s*\\+\\s*\"(?:\\\\.|[^\"\\\\])*\")*";
    private static final Pattern UI_TEXT = Pattern.compile(
            "(?:Text|SettingsRow|SettingsSection|SettingsHint|SettingsSwitchRow|SummaryCard|MoreScaffold"
                    + "|EmptyState|ErrorState)\\(\\s*(?<expression>" + EXPRESSION + ")",
            Pattern.DOTALL);
    private static final Pattern CONTENT_DESCRIPTION = Pattern.compile(
            "contentDescription\\s*=\\s*(?<expression>" + EXPRESSION + ")",
            Pattern.DOTALL);
    private static final Pattern SETTINGS_DESCRIPTION = Pattern.compile(
            "(?:SettingsRow|SettingsSwitchRow)\\(\\s*" + EXPRESSION
                    + "\\s*,\\s*(?<expression>" + EXPRESSION + ")",
            Pattern.DOTALL);
    private static final Pattern STRING = Pattern.compile("\"((?:\\\\.|[^\"\\\\])*)\"");
    private static final Set<String> UNIVERSAL = Set.of(
            "", "-50", "+50", "↑", "↓", "Anilib", "Anime", "Manga", "PiP", "SHA-256");

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
            String translations = readCatalogs(repository.root(), catalog, diagnostics);
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

    private String readCatalogs(
            Path repository,
            Path platformCatalog,
            List<Diagnostic> diagnostics) throws IOException {
        StringBuilder translations = new StringBuilder(
                Files.readString(platformCatalog, StandardCharsets.UTF_8));
        Path features = repository.resolve(FEATURES_ROOT);
        if (!Files.isDirectory(features) || Files.isSymbolicLink(features)) {
            return translations.toString();
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
                translations.append('\n').append(Files.readString(catalogs.getFirst(), StandardCharsets.UTF_8));
            }
        }
        return translations.toString();
    }

    private void inspect(
            Path repository,
            Path file,
            String translations,
            List<Diagnostic> diagnostics) throws IOException {
        String content = Files.readString(file, StandardCharsets.UTF_8);
        Path relative = repository.relativize(file);
        if (!file.endsWith("LocalizedText.kt") && (content.contains("import androidx.compose.material3.Text\n")
                || content.contains("import androidx.compose.material3.Icon\n"))) {
            diagnostics.add(new Diagnostic(name(), relative, 1,
                    "Shared UI must use the localization-aware Text and Icon adapters"));
        }
        requireCatalogEntries(relative, content, translations, UI_TEXT, diagnostics);
        requireCatalogEntries(relative, content, translations, SETTINGS_DESCRIPTION, diagnostics);
        requireCatalogEntries(relative, content, translations, CONTENT_DESCRIPTION, diagnostics);
    }

    private void requireCatalogEntries(
            Path relative,
            String content,
            String translations,
            Pattern pattern,
            List<Diagnostic> diagnostics) {
        Matcher calls = pattern.matcher(content);
        while (calls.find()) {
            String value = join(calls.group("expression"));
            if (value.contains("$") || UNIVERSAL.contains(value)) {
                continue;
            }
            String evidence = value.substring(0, Math.min(48, value.length()));
            if (!translations.contains(evidence)) {
                diagnostics.add(new Diagnostic(name(), relative, line(content, calls.start()),
                        "French translation is missing for UI text: " + evidence));
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
