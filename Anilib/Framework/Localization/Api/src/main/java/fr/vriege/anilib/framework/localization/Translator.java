package fr.vriege.anilib.framework.localization;

import fr.vriege.anilib.foundation.component.ComponentId;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Resolves user-facing keys or compatibility messages across feature-owned
 * translation catalogs with a safe source-text fallback.
 *
 * <p>Catalogs are consulted in constructor order. Each component may contribute
 * at most one catalog; duplicate owner identities are rejected. If no catalog
 * supplies a translation for the requested language and exact source message,
 * the source message itself is returned.</p>
 *
 * <p>Instances are immutable and safe to share between threads.</p>
 */
public final class Translator {
    private final List<TranslationCatalog> catalogs;

    /**
     * Creates a translator from an ordered catalog snapshot.
     *
     * @param catalogs the non-null ordered catalogs; may be empty
     * @throws NullPointerException if {@code catalogs} or one of its elements is
     *                              {@code null}
     * @throws IllegalArgumentException if two catalogs have the same owner
     */
    public Translator(List<TranslationCatalog> catalogs) {
        Objects.requireNonNull(catalogs, "catalogs");
        this.catalogs = List.copyOf(catalogs);
        Set<ComponentId> owners = new HashSet<>();
        for (TranslationCatalog catalog : this.catalogs) {
            if (!owners.add(catalog.owner())) {
                throw new IllegalArgumentException("Duplicate translation catalog: " + catalog.owner());
            }
        }
    }

    /**
     * Translates an exact source message or returns it unchanged.
     *
     * <p>Blank source strings are returned immediately. For non-blank messages,
     * catalogs are queried in their configured order and the first matching
     * translation wins.</p>
     *
     * @param languageTag the non-null IETF BCP 47 language tag
     * @param source      the non-null source message and fallback value
     * @return the first matching translation, or {@code source} when none is
     *         available
     * @throws NullPointerException if either argument is {@code null}
     */
    public String translate(String languageTag, String source) {
        Objects.requireNonNull(languageTag, "languageTag");
        Objects.requireNonNull(source, "source");
        if (source.isBlank()) {
            return source;
        }
        for (TranslationCatalog catalog : catalogs) {
            var translated = catalog.translate(languageTag, source);
            if (translated.isPresent()) {
                return translated.orElseThrow();
            }
        }
        return source;
    }

    public String format(String languageTag, String key, List<String> arguments) {
        Objects.requireNonNull(arguments, "arguments");
        String result = translate(languageTag, key);
        for (int index = 0; index < arguments.size(); index++) {
            String argument = Objects.requireNonNull(arguments.get(index), "argument");
            result = result.replace("{" + index + "}", argument);
        }
        return result;
    }
}
