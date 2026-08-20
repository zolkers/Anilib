package fr.vriege.anilib.tooling.archtests;

import fr.vriege.anilib.tooling.javaquality.LocalizationRule;
import fr.vriege.anilib.tooling.javaquality.RepositorySnapshot;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

final class LocalizationRuleTest {
    private LocalizationRuleTest() {
    }

    static int run() {
        Path repository = temporaryDirectory();
        try {
            Path root = repository.resolve(
                    "Anilib/Platforms/Compose/src/shared/kotlin/fr/vriege/anilib/platform/compose");
            Files.createDirectories(root);
            Files.writeString(root.resolve("UiTranslations.kt"),
                    "TranslationCatalog.resources()", StandardCharsets.UTF_8);
            Path platformCatalog = repository.resolve("Anilib/Platforms/Compose/src/shared/resources/"
                    + "META-INF/anilib/i18n/platform-compose");
            writeCatalog(
                    platformCatalog,
                    "action.save=Save\ndynamic.progress=Progress: {0}\nfield.details=Details\n",
                    "action.save=Enregistrer\ndynamic.progress=Progression : {0}\nfield.details=Détails\n");
            Path featureUi = repository.resolve("Anilib/Features/Library/Ui/src/main/java/example");
            Files.createDirectories(featureUi);
            Path featureCatalog = featureUi.resolve("LibraryTranslationCatalog.java");
            Files.writeString(featureCatalog, "TranslationCatalog.resources()", StandardCharsets.UTF_8);
            writeCatalog(
                    repository.resolve("Anilib/Features/Library/Ui/src/main/resources/"
                            + "META-INF/anilib/i18n/library"),
                    "library.favorite=Favorite\n",
                    "library.favorite=Ajouter aux favoris\n");
            Path screen = root.resolve("Screen.kt");
            Files.writeString(screen, "fun screen() { Text(\"action.save\") }", StandardCharsets.UTF_8);
            LocalizationRule rule = new LocalizationRule();
            RepositorySnapshot snapshot = new RepositorySnapshot(
                    repository, List.of(), List.of(), List.of(), List.of());
            check(rule.analyze(snapshot).isEmpty(), "catalogued shared UI text must pass localization");
            Files.writeString(screen, "fun screen() { SettingsRow(\"action.save\", \"field.details\") }",
                    StandardCharsets.UTF_8);
            check(rule.analyze(snapshot).isEmpty(), "catalogued settings descriptions must pass localization");
            Files.writeString(screen,
                    "fun screen() { Text(UiTranslations.format(\"dynamic.progress\", language, position)) }",
                    StandardCharsets.UTF_8);
            check(rule.analyze(snapshot).isEmpty(), "catalogued dynamic UI text must pass localization");
            Files.writeString(screen, "fun screen() { MoreRow(\"action.save\", \"field.details\") }",
                    StandardCharsets.UTF_8);
            check(rule.analyze(snapshot).isEmpty(), "custom UI components must accept catalogued keys");
            Files.writeString(screen, "fun screen() { MoreRow(\"action.save\", \"Raw summary\") }",
                    StandardCharsets.UTF_8);
            check(rule.analyze(snapshot).stream().anyMatch(diagnostic ->
                            diagnostic.message().contains("Raw summary")),
                    "custom UI component summaries must use resource keys");
            Files.writeString(screen, "fun screen() { Text(\"Progress: $position\") }", StandardCharsets.UTF_8);
            check(rule.analyze(snapshot).stream().anyMatch(diagnostic ->
                            diagnostic.message().contains("parameterized translation key")),
                    "raw interpolated UI text must fail the quality gate");
            Files.writeString(screen, "fun screen() { SettingsRow(\"Save\", \"Untranslated detail\") }",
                    StandardCharsets.UTF_8);
            check(rule.analyze(snapshot).stream().anyMatch(diagnostic ->
                            diagnostic.message().contains("Untranslated detail")),
                    "new untranslated settings descriptions must fail the quality gate");
            Files.writeString(screen, "fun screen() { Text(\"Untranslated action\") }", StandardCharsets.UTF_8);
            check(rule.analyze(snapshot).stream().anyMatch(diagnostic ->
                            diagnostic.message().contains("Untranslated action")),
                    "new untranslated shared UI text must fail the quality gate");
            Files.writeString(featureCatalog, "TranslationCatalog.french(Map.entry())", StandardCharsets.UTF_8);
            check(rule.analyze(snapshot).stream().anyMatch(diagnostic ->
                            diagnostic.message().contains("Translation maps must live")),
                    "hardcoded translation maps must fail the quality gate");
            Files.writeString(featureCatalog, "TranslationCatalog.resources()", StandardCharsets.UTF_8);
            Files.writeString(
                    platformCatalog.resolve("en.properties"),
                    "action.save=Save\ndynamic.progress=Progress: {0}\nfield.details=Details\naction.save=Duplicate\n",
                    StandardCharsets.UTF_8);
            check(rule.analyze(snapshot).stream().anyMatch(diagnostic ->
                            diagnostic.message().contains("Duplicate translation key")),
                    "duplicate resource keys must fail the quality gate");
            Files.writeString(
                    platformCatalog.resolve("en.properties"),
                    "action.save=Save\ndynamic.progress=Progress: {0}\nfield.details=Details\n",
                    StandardCharsets.UTF_8);
            Files.delete(featureCatalog);
            check(rule.analyze(snapshot).stream().anyMatch(diagnostic ->
                            diagnostic.message().contains("exactly one translation catalog")),
                    "a feature UI without its own catalog must fail the quality gate");
            return 11;
        } catch (IOException exception) {
            throw new AssertionError("Unable to test localization rule", exception);
        } finally {
            deleteDirectory(repository);
        }
    }

    private static void writeCatalog(
            Path directory,
            String english,
            String french) throws IOException {
        Files.createDirectories(directory);
        Files.writeString(directory.resolve("en.properties"), english, StandardCharsets.UTF_8);
        Files.writeString(directory.resolve("fr.properties"), french, StandardCharsets.UTF_8);
    }

    private static Path temporaryDirectory() {
        try {
            return Files.createTempDirectory("anilib-localization-rule");
        } catch (IOException exception) {
            throw new AssertionError("Unable to create localization rule fixture", exception);
        }
    }

    private static void deleteDirectory(Path directory) {
        try (var paths = Files.walk(directory)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        } catch (IOException exception) {
            throw new AssertionError("Unable to clean localization rule fixture", exception);
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
