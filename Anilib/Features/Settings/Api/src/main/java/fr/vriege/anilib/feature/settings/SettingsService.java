package fr.vriege.anilib.feature.settings;

import java.util.function.Consumer;

public interface SettingsService {
    SettingsSnapshot snapshot();

    void replace(SettingsSnapshot settings);

    AutoCloseable observe(Consumer<SettingsSnapshot> observer);
}
