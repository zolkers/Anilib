package fr.vriege.anilib.feature.covercache;

import java.util.Objects;

public record CoverKey(String value) {
    public CoverKey {
        value = Objects.requireNonNull(value, "value must not be null").trim();
        if (value.isEmpty()) {
            throw new IllegalArgumentException("value must not be blank");
        }
    }
}
