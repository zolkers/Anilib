package fr.vriege.anilib.feature.discovery;

public interface DiscoveryBrowsePreferenceStore {
    DiscoveryBrowsePreferences snapshot();

    void save(DiscoveryBrowsePreferences preferences);
}
