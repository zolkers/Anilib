package fr.vriege.anilib.feature.extensionrepository.runtime;

import fr.vriege.anilib.foundation.validation.Preconditions;
import fr.vriege.anilib.feature.source.SourcePreferenceDefinition;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public record AniyomiSourcePreferences(
        List<SourcePreferenceDefinition> definitions,
        Consumer<Map<String, String>> valueSink) {
    private static final AniyomiSourcePreferences EMPTY = new AniyomiSourcePreferences(
            List.of(),
            ignored -> { });

    public AniyomiSourcePreferences {
        definitions = List.copyOf(Preconditions.requireNonNull(definitions, "definitions"));
        Preconditions.requireNonNull(valueSink, "valueSink");
    }

    public static AniyomiSourcePreferences empty() {
        return EMPTY;
    }

    void apply(Map<String, String> values) {
        valueSink.accept(Map.copyOf(Preconditions.requireNonNull(values, "values")));
    }
}
