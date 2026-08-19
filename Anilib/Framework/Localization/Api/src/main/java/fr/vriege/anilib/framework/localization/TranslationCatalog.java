package fr.vriege.anilib.framework.localization;

import fr.vriege.anilib.foundation.component.ComponentId;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public record TranslationCatalog(ComponentId owner, Map<String, String> french) {
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

    public static TranslationCatalog french(String owner, Map<String, String> messages) {
        return new TranslationCatalog(ComponentId.of(owner), messages);
    }

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
