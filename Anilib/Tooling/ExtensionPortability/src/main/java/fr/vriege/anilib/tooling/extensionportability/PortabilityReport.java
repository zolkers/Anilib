package fr.vriege.anilib.tooling.extensionportability;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record PortabilityReport(
        Path sourceRepository,
        Optional<String> packageIdentity,
        List<String> sourceIds,
        List<PortabilityFinding> findings,
        int inspectedFiles) {
    public PortabilityReport {
        sourceRepository = Objects.requireNonNull(sourceRepository, "sourceRepository must not be null")
                .toAbsolutePath()
                .normalize();
        packageIdentity = Objects.requireNonNull(packageIdentity, "packageIdentity must not be null")
                .map(String::strip)
                .filter(value -> !value.isEmpty());
        sourceIds = List.copyOf(Objects.requireNonNull(sourceIds, "sourceIds must not be null"));
        findings = List.copyOf(Objects.requireNonNull(findings, "findings must not be null"));
        if (sourceIds.stream().anyMatch(String::isBlank)) {
            throw new IllegalArgumentException("sourceIds must not contain blank values");
        }
        if (inspectedFiles < 0) {
            throw new IllegalArgumentException("inspectedFiles must not be negative");
        }
    }

    public boolean hasBlockers() {
        return findings.stream().anyMatch(finding -> finding.severity() == PortabilitySeverity.BLOCKED);
    }

    public boolean requiresAdaptation() {
        return findings.stream().anyMatch(
                finding -> finding.severity() == PortabilitySeverity.ADAPTATION_REQUIRED);
    }
}
