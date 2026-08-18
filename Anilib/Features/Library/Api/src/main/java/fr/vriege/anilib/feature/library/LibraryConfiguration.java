package fr.vriege.anilib.feature.library;

public interface LibraryConfiguration {
    LibraryConfigurationSnapshot snapshot();

    void save(LibraryConfigurationSnapshot snapshot);
}
