package fr.vriege.anilib.feature.settings;

import java.util.Map;
import java.util.Objects;

public record UnusedDataCleanupResult(Map<String, Integer> removedByOwner) {
    public UnusedDataCleanupResult {
        Objects.requireNonNull(removedByOwner, "removedByOwner must not be null");
        removedByOwner = Map.copyOf(removedByOwner);
        if (removedByOwner.entrySet().stream()
                .anyMatch(entry -> entry.getKey().isBlank() || entry.getValue() == null || entry.getValue() < 0)) {
            throw new IllegalArgumentException("cleanup results must use named owners and non-negative counts");
        }
    }

    public int totalRemoved() {
        return removedByOwner.values().stream().mapToInt(Integer::intValue).sum();
    }

    public static UnusedDataCleanupResult empty() {
        return new UnusedDataCleanupResult(Map.of());
    }
}
