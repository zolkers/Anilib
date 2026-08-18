package fr.vriege.anilib.feature.source;

import fr.vriege.anilib.foundation.validation.Preconditions;

import java.util.Locale;
import java.util.Set;

public record SourceDescriptor(
        SourceId id,
        String displayName,
        String extensionVersion,
        String languageTag,
        Set<SourceContentKind> contentKinds,
        SourceApiVersion requiredApiVersion) {

    public SourceDescriptor {
        Preconditions.requireNonNull(id, "id");
        Preconditions.requireNonBlank(displayName, "displayName");
        Preconditions.requireNonBlank(extensionVersion, "extensionVersion");
        languageTag = normalizedLanguageTag(languageTag);
        contentKinds = Set.copyOf(contentKinds);
        if (contentKinds.isEmpty()) {
            throw new IllegalArgumentException("contentKinds must not be empty");
        }
        Preconditions.requireNonNull(requiredApiVersion, "requiredApiVersion");
    }

    private static String normalizedLanguageTag(String languageTag) {
        String value = Preconditions.requireNonBlank(languageTag, "languageTag");
        String normalized = Locale.forLanguageTag(value).toLanguageTag();
        if (normalized.equals("und") && !value.equalsIgnoreCase("und")) {
            throw new IllegalArgumentException("Invalid BCP 47 language tag: " + value);
        }
        return normalized;
    }
}
