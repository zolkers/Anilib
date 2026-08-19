package fr.vriege.anilib.framework.localization;

import fr.vriege.anilib.foundation.component.ComponentId;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * An immutable set of French translations owned by one product component.
 *
 * <p>Source messages are the stable lookup keys and also serve as the fallback
 * text when no translation is available. Keys and translations are non-blank,
 * compared exactly, and defensively copied into an immutable map. The owner
 * identity lets {@link Translator} reject accidental duplicate catalogs from
 * the same component.</p>
 *
 * @param owner  the component that owns this catalog
 * @param french the exact source-message to French-translation mapping
 */
public record TranslationCatalog(ComponentId owner, Map<String, String> french) {
    /**
     * Creates an immutable French translation catalog.
     *
     * @throws NullPointerException if {@code owner}, {@code french}, a source
     *                              key, or a translation value is {@code null}
     * @throws IllegalArgumentException if a source key or translation is blank
     */
    public TranslationCatalog {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(french, "french");
        LinkedHashMap<String, String> copy = new LinkedHashMap<>();
        french.forEach((source, translation) -> {
            if (source == null || source.isBlank()) {
                throw new IllegalArgumentException("Translation source must not be blank");
            }
            if (translation == null || translation.isBlank()) {
                throw new IllegalArgumentException("Translation must not be blank for " + source);
            }
            copy.put(source, translation);
        });
        french = Map.copyOf(copy);
    }

    /**
     * Creates a French catalog from a textual component identifier.
     *
     * @param owner    the stable owner identifier accepted by
     *                 {@link ComponentId#of(String)}
     * @param messages the source-message to French-translation mapping
     * @return an immutable translation catalog
     * @throws NullPointerException if an argument, key, or value is {@code null}
     * @throws IllegalArgumentException if the owner identifier is invalid or a
     *                                  message key or translation is blank
     */
    public static TranslationCatalog french(String owner, Map<String, String> messages) {
        return new TranslationCatalog(ComponentId.of(owner), messages);
    }

    /**
     * Looks up an exact source message for a language tag.
     *
     * <p>Any well-formed tag whose base language is French, such as {@code fr}
     * or {@code fr-FR}, is eligible. Other languages and missing messages return
     * an empty optional.</p>
     *
     * @param languageTag the non-null IETF BCP 47 language tag
     * @param source      the non-null exact source message
     * @return the French translation, or an empty optional when this catalog
     *         does not apply or has no matching message
     * @throws NullPointerException if either argument is {@code null}
     */
    public Optional<String> translate(String languageTag, String source) {
        Objects.requireNonNull(languageTag, "languageTag");
        Objects.requireNonNull(source, "source");
        String language = Locale.forLanguageTag(languageTag).getLanguage();
        if (!Locale.FRENCH.getLanguage().equals(language)) {
            return Optional.empty();
        }
        return Optional.ofNullable(french.get(source));
    }
}
