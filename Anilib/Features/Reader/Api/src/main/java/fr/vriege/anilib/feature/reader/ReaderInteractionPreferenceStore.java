package fr.vriege.anilib.feature.reader;

public interface ReaderInteractionPreferenceStore {
    ReaderInteractionPreferences snapshot();

    void save(ReaderInteractionPreferences preferences);
}
