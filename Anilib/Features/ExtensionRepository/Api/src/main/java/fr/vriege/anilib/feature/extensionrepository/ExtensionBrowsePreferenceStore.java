package fr.vriege.anilib.feature.extensionrepository;

public interface ExtensionBrowsePreferenceStore {
    ExtensionBrowsePreferences snapshot();

    void save(ExtensionBrowsePreferences preferences);
}
