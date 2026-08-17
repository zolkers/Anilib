package fr.vriege.anilib.feature.source;

import fr.vriege.anilib.foundation.validation.Preconditions;

import java.util.HashSet;
import java.util.List;

/** Source preference schema rendered identically by Android and desktop. */
public record SourcePreferenceDefinition(
        String id,
        String title,
        String summary,
        SourcePreferenceType type,
        List<String> options,
        String defaultValue,
        boolean sensitive) {
    public SourcePreferenceDefinition {
        id = Preconditions.requireNonBlank(id, "id");
        title = Preconditions.requireNonBlank(title, "title");
        summary = Preconditions.requireNonNull(summary, "summary");
        Preconditions.requireNonNull(type, "type");
        options = List.copyOf(Preconditions.requireNonNull(options, "options"));
        defaultValue = Preconditions.requireNonNull(defaultValue, "defaultValue");
        if (options.stream().anyMatch(String::isBlank)
                || new HashSet<>(options).size() != options.size()) {
            throw new IllegalArgumentException("options must be non-blank and unique");
        }
        switch (type) {
            case TEXT -> requireEmptyOptions(options);
            case SWITCH -> {
                requireEmptyOptions(options);
                if (!defaultValue.equals("true") && !defaultValue.equals("false")) {
                    throw new IllegalArgumentException("switch defaultValue must be true or false");
                }
            }
            case SELECT -> {
                if (options.isEmpty() || !options.contains(defaultValue)) {
                    throw new IllegalArgumentException("select defaultValue must name an option");
                }
            }
        }
    }

    private static void requireEmptyOptions(List<String> options) {
        if (!options.isEmpty()) {
            throw new IllegalArgumentException("text and switch preferences cannot declare options");
        }
    }
}
