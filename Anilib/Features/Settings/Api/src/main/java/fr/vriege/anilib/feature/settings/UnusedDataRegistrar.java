package fr.vriege.anilib.feature.settings;

public interface UnusedDataRegistrar {
    AutoCloseable register(String owner, UnusedDataCleaner cleaner);
}
