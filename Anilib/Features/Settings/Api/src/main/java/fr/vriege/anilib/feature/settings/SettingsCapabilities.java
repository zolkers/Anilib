package fr.vriege.anilib.feature.settings;

import fr.vriege.anilib.kernel.CapabilityKey;

/** Typed settings services published by the Settings Bundle. */
public final class SettingsCapabilities {
    public static final CapabilityKey<SettingsService> SERVICE =
            CapabilityKey.of("feature.settings.service", SettingsService.class);

    private SettingsCapabilities() {
    }
}
