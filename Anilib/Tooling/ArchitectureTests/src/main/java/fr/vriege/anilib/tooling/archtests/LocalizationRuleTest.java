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
                    "LanguagePack.FRENCH Save Enregistrer", StandardCharsets.UTF_8);
            Path screen = root.resolve("Screen.kt");
            Files.writeString(screen, "fun screen() { Text(\"Save\") }", StandardCharsets.UTF_8);
            LocalizationRule rule = new LocalizationRule();
            RepositorySnapshot snapshot = new RepositorySnapshot(
                    repository, List.of(), List.of(), List.of(), List.of());
            check(rule.analyze(snapshot).isEmpty(), "catalogued shared UI text must pass localization");
            Files.writeString(screen, "fun screen() { Text(\"Untranslated action\") }", StandardCharsets.UTF_8);
            check(rule.analyze(snapshot).stream().anyMatch(diagnostic ->
                            diagnostic.message().contains("Untranslated action")),
                    "new untranslated shared UI text must fail the quality gate");
            return 2;
        } catch (IOException exception) {
            throw new AssertionError("Unable to test localization rule", exception);
        } finally {
            deleteDirectory(repository);
        }
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
