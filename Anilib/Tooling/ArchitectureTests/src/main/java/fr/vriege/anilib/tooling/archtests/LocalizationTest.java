package fr.vriege.anilib.tooling.archtests;

import fr.vriege.anilib.feature.extensionrepository.ui.ExtensionRepositoryTranslationCatalog;
import fr.vriege.anilib.framework.localization.TranslationCatalog;
import fr.vriege.anilib.framework.localization.Translator;

import java.util.List;

final class LocalizationTest {
    private LocalizationTest() {
    }

    static int run() {
        TranslationCatalog catalog = TranslationCatalog.resources(
                "feature.example",
                LocalizationTest.class,
                "META-INF/anilib/i18n/localization-test");
        Translator translator = new Translator(List.of(catalog));
        check("Enregistrer".equals(translator.translate("fr-FR", "Save")),
                "French regional tags must resolve the feature translation");
        check("Save".equals(translator.translate("en", "Save")),
                "English must keep the source message");
        check("Missing".equals(translator.translate("fr", "Missing")),
                "missing translations must safely fall back to English");
        check("Progression : 03:20".equals(translator.translate("fr", "Progress: 03:20")),
                "resource templates must translate dynamic values without hardcoded maps");
        check("VOSTFR, VF, VA · Progression : 03:20".equals(
                        translator.translate("fr", "VOSTFR, VF, VA · Progress: 03:20")),
                "resource templates must translate progress embedded in episode metadata");
        check("Progression : 03:20".equals(
                        translator.format("fr", "history.progress", List.of("03:20"))),
                "dynamic UI must format explicit resource keys");
        Translator resources = new Translator(List.of(ExtensionRepositoryTranslationCatalog.catalog()));
        check("Browse extensions".equals(resources.translate("en", "extensions.browse")),
                "resource keys must resolve to their English message");
        check("Parcourir les extensions".equals(resources.translate("fr", "extensions.browse")),
                "resource keys must resolve to their French message");
        check("Parcourir les extensions".equals(resources.translate("fr", "Browse extensions")),
                "English messages must remain a compatible migration alias");
        checkThrows(() -> new Translator(List.of(catalog, catalog)),
                "duplicate feature catalogs must be rejected");
        return 10;
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static void checkThrows(Runnable action, String message) {
        try {
            action.run();
            throw new AssertionError(message);
        } catch (IllegalArgumentException expected) {
            // Expected.
        }
    }
}
