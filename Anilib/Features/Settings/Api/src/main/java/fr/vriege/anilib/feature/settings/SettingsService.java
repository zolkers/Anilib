package fr.vriege.anilib.feature.settings;

import java.util.function.Consumer;

/** Durable settings port with immediate snapshot observation. */
public interface SettingsService {
    SettingsSnapshot snapshot();

    void replace(SettingsSnapshot settings);

    AutoCloseable observe(Consumer<SettingsSnapshot> observer);
}
