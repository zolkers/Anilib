package fr.vriege.anilib.feature.reader;

public interface ReaderDisplayPreferenceStore {
    ReaderDisplayPreferences snapshot();

    void save(ReaderDisplayPreferences preferences);
}
