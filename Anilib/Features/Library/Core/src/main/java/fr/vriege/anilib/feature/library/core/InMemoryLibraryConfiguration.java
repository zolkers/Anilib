package fr.vriege.anilib.feature.library.core;

import fr.vriege.anilib.feature.library.LibraryConfiguration;
import fr.vriege.anilib.feature.library.LibraryConfigurationSnapshot;

import java.util.Objects;

public final class InMemoryLibraryConfiguration implements LibraryConfiguration {
    private LibraryConfigurationSnapshot snapshot;

    public InMemoryLibraryConfiguration() {
        this(LibraryConfigurationSnapshot.defaults());
    }

    public InMemoryLibraryConfiguration(LibraryConfigurationSnapshot snapshot) {
        this.snapshot = Objects.requireNonNull(snapshot, "snapshot must not be null");
    }

    @Override
    public synchronized LibraryConfigurationSnapshot snapshot() {
        return snapshot;
    }

    @Override
    public synchronized void save(LibraryConfigurationSnapshot replacement) {
        snapshot = Objects.requireNonNull(replacement, "replacement must not be null");
    }
}
