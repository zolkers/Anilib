package fr.vriege.anilib.feature.settings;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public record DiagnosticResetPlan(
        String confirmationToken,
        Set<DiagnosticResetArea> areas,
        List<Path> targets,
        long reclaimableBytes) {
    public DiagnosticResetPlan {
        confirmationToken = Objects.requireNonNull(
                confirmationToken,
                "confirmationToken must not be null");
        areas = Set.copyOf(areas);
        targets = targets.stream().map(path -> path.toAbsolutePath().normalize()).toList();
        if (confirmationToken.isBlank() || areas.isEmpty() || targets.isEmpty() || reclaimableBytes < 0) {
            throw new IllegalArgumentException("reset plan values are invalid");
        }
    }
}
