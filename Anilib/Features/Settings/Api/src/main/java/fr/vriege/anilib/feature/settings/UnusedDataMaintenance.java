package fr.vriege.anilib.feature.settings;

@FunctionalInterface
public interface UnusedDataMaintenance {
    UnusedDataCleanupResult clean();
}
