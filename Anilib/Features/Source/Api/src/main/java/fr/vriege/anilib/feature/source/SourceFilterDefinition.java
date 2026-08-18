package fr.vriege.anilib.feature.source;

import fr.vriege.anilib.foundation.validation.Preconditions;

import java.util.HashSet;
import java.util.List;

public record SourceFilterDefinition(
        String id,
        String label,
        SourceFilterType type,
        List<String> options,
        String defaultValue,
        String groupId) {
    public SourceFilterDefinition {
        id = Preconditions.requireNonBlank(id, "id");
        label = Preconditions.requireNonNull(label, "label");
        Preconditions.requireNonNull(type, "type");
        options = List.copyOf(Preconditions.requireNonNull(options, "options"));
        defaultValue = Preconditions.requireNonNull(defaultValue, "defaultValue");
        groupId = Preconditions.requireNonNull(groupId, "groupId");
        if (options.stream().anyMatch(String::isBlank)
                || new HashSet<>(options).size() != options.size()) {
            throw new IllegalArgumentException("options must be non-blank and unique");
        }
        validateShape(type, options, defaultValue);
    }

    private static void validateShape(SourceFilterType type, List<String> options, String defaultValue) {
        switch (type) {
            case HEADER, SEPARATOR -> {
                if (!options.isEmpty() || !defaultValue.isEmpty()) {
                    throw new IllegalArgumentException(type + " filters cannot have values");
                }
            }
            case TEXT -> requireNoOptions(type, options);
            case CHECKBOX -> {
                requireNoOptions(type, options);
                requireOneOf(defaultValue, List.of("true", "false"), type);
            }
            case TRI_STATE -> {
                requireNoOptions(type, options);
                requireOneOf(defaultValue, List.of("ignore", "include", "exclude"), type);
            }
            case SELECT, SORT -> {
                if (options.isEmpty()) {
                    throw new IllegalArgumentException(type + " filters require options");
                }
                requireOneOf(defaultValue, options, type);
            }
        }
    }

    private static void requireNoOptions(SourceFilterType type, List<String> options) {
        if (!options.isEmpty()) {
            throw new IllegalArgumentException(type + " filters cannot declare options");
        }
    }

    private static void requireOneOf(String value, List<String> allowed, SourceFilterType type) {
        if (!allowed.contains(value)) {
            throw new IllegalArgumentException(type + " filter has an invalid default value");
        }
    }
}
