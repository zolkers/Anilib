package fr.vriege.anilib.framework.localization;

import fr.vriege.anilib.foundation.component.ComponentId;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class Translator {
    private final List<TranslationCatalog> catalogs;

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
}
